package semantic;

import ast.template.TemplateNode;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.StringLiteralNode;
import ast.visitors.TemplateBaseASTVisitor;

import java.io.File;
import java.util.*;

/**
 * The 11 Jinja2 semantic checks, running on the project's own AST instead of
 * the ANTLR parse tree — the Jinja counterpart of PythonSemanticAnalyzer.
 * <p>
 * ERRORS
 * 1. Duplicate block name in the same template
 * 2. {% extends %} used more than once
 * 3. Template extends itself (circular dependency)
 * 11. Template variable never passed from render_template   (spec #5)
 * <p>
 * WARNINGS
 * 4. Unknown Jinja2 filter name
 * 5. For-loop variable used after the loop has ended
 * 6. {% include %} references a file that does not exist
 * 7. {% set %} redefines a variable passed from Python
 * 8. {% set %} redefines a variable already set in this template
 * 9. {% extends %} is not the first Jinja2 statement
 * 10. {% for %} iterates a literal empty list
 */
public class JinjaAstSemanticAnalyzer extends TemplateBaseASTVisitor<Void> {

    private final String templateName;
    private final String templateDir;
    private final Set<String> pythonContextVars;
    private final Map<String, Object> mockData;
    private final Set<String> allMockDataKeys = new LinkedHashSet<>();

    private final List<SemanticError> errors = new ArrayList<>();
    private final List<SemanticError> warnings = new ArrayList<>();

    private final Map<String, Integer> seenBlocks = new LinkedHashMap<>();
    private final Map<String, Integer> setVarLines = new LinkedHashMap<>();

    private int extendsCount = 0;
    private boolean sawJinjaBeforeExtends = false;

    private final Deque<String> activeLoopVars = new ArrayDeque<>();
    private final Deque<String> activeWithVars = new ArrayDeque<>();
    private final Set<String> expiredLoopVars = new LinkedHashSet<>();

    private static final Set<String> BUILTIN_NAMES = Set.of(
            "loop", "range", "dict", "list", "cycler", "namespace", "lipsum", "joiner",
            "url_for", "get_flashed_messages", "request", "session", "g", "config",
            "self", "super", "true", "false", "none", "True", "False", "None"
    );

    private static final Set<String> KNOWN_FILTERS = Set.of(
            "abs", "attr", "batch", "capitalize", "center", "count", "d", "default", "dictsort",
            "e", "escape", "filesizeformat", "first", "float", "forceescape", "format", "groupby",
            "indent", "int", "items", "join", "last", "length", "list", "lower", "map", "max", "min",
            "pprint", "random", "reject", "rejectattr", "replace", "reverse", "round", "safe",
            "select", "selectattr", "slice", "sort", "string", "striptags", "sum", "title", "tojson",
            "trim", "truncate", "unique", "upper", "urlencode", "urlize", "wordcount", "wordwrap",
            "xmlattr", "nl2br", "b64encode", "b64decode"
    );

    private int maxListSize = 0;

    public JinjaAstSemanticAnalyzer(String templateName, String templateDir,
                                    Set<String> pythonContextVars, Map<String, Object> mockData) {
        this.templateName = templateName;
        this.templateDir = templateDir;
        this.pythonContextVars = pythonContextVars != null ? pythonContextVars : Set.of();
        this.mockData = mockData != null ? mockData : Map.of();
        collectKeys(this.mockData);
    }

    public JinjaAstSemanticAnalyzer(String templateName, String templateDir, Set<String> pythonContextVars) {
        this(templateName, templateDir, pythonContextVars, null);
    }

    private void collectKeys(Object obj) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object key : map.keySet()) {
                if (key instanceof String) {
                    allMockDataKeys.add((String) key);
                }
            }
            for (Object val : map.values()) {
                collectKeys(val);
            }
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.size() > maxListSize) {
                maxListSize = list.size();
            }
            for (Object item : list) {
                collectKeys(item);
            }
        }
    }

    private void error(String m, int l, int c) {
        errors.add(new SemanticError(SemanticError.Severity.ERROR, m, l, c));
    }

    private void warning(String m, int l, int c) {
        warnings.add(new SemanticError(SemanticError.Severity.WARNING, m, l, c));
    }

    public List<SemanticError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<SemanticError> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  JINJA2 SEMANTIC ANALYSIS: " + templateName + "  (12 checks)");
        System.out.println("=".repeat(60));
        if (errors.isEmpty() && warnings.isEmpty()) {
            System.out.println("  No semantic issues found.");
            return;
        }
        if (!errors.isEmpty()) {
            System.out.println("\n  ERRORS (" + errors.size() + "):");
            errors.forEach(e -> System.out.println("  " + e));
        }
        if (!warnings.isEmpty()) {
            System.out.println("\n  WARNINGS (" + warnings.size() + "):");
            warnings.forEach(w -> System.out.println("  " + w));
        }
        System.out.printf("%n  Summary: %d error(s), %d warning(s) = %d total issue(s)%n",
                errors.size(), warnings.size(), errors.size() + warnings.size());
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Override
    public Void visit(BlockBlockNode node) {
        String name = node.getBlockName();
        if (seenBlocks.containsKey(name)) {
            error("Block '" + name + "' is defined more than once (first at line "
                    + seenBlocks.get(name) + ")", node.getLine(), node.getColumn());
        } else {
            seenBlocks.put(name, node.getLine());
        }
        sawJinjaBeforeExtends = true;
        return super.visit(node);
    }

    @Override
    public Void visit(ExtendsBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        int line = node.getLine(), col = node.getColumn();

        if (++extendsCount > 1) {
            error("{% extends %} used more than once in template '" + templateName + "'",
                    line, col);
        }
        if (templateName.equals(target)) {
            error("Template '" + templateName + "' extends itself — circular dependency",
                    line, col);
        }
        if (sawJinjaBeforeExtends) {
            warning("{% extends \"" + target + "\" %} should be the first Jinja2 "
                    + "statement but other Jinja2 content precedes it", line, col);
        }
        
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% extends \"" + target + "\" %} references a file that does not exist: " + targetFile.getPath(), line, col);
        }

        return super.visit(node);
    }

    @Override
    public Void visit(ForBlockNode node) {
        String loopVar = node.getVariable();
        sawJinjaBeforeExtends = true;

        if (node.getIterable() instanceof ListExpressionNode list
                && list.getElements().isEmpty()) {
            warning("{% for " + loopVar + " in [] %} iterates over a literal empty list"
                    + " — loop body never executes", node.getLine(), node.getColumn());
        }

        // The block owns its body, so the variable's scope is exactly this subtree.
        visitChild(node.getIterable());
        activeLoopVars.push(loopVar);
        for (TemplateNode c : node.getContent()) visitChild(c);
        activeLoopVars.pop();
        expiredLoopVars.add(loopVar);

        if (node.hasElseBlock()) visitChild(node.getElseBlock());
        return null;
    }

    @Override
    public Void visit(WithBlockNode node) {
        sawJinjaBeforeExtends = true;
        visitChild(node.getExpression());

        boolean bound = node.hasVariable();
        if (bound) activeWithVars.push(node.getVariable());
        for (TemplateNode c : node.getContent()) visitChild(c);
        if (bound) activeWithVars.pop();
        return null;
    }

    @Override
    public Void visit(SetBlockNode node) {
        String name = node.getVariable();
        int line = node.getLine(), col = node.getColumn();

        if (pythonContextVars.contains(name)) {
            warning("{% set " + name + " = ... %} redefines the variable '" + name
                    + "' that was passed from Python via render_template()", line, col);
        }
        if (setVarLines.containsKey(name)) {
            warning("{% set " + name + " = ... %} redefines a template variable '" + name
                             + "' already defined via {% set %} at line " + setVarLines.get(name),
                    line, col);
        } else {
            setVarLines.put(name, line);
        }
        sawJinjaBeforeExtends = true;
        return super.visit(node);
    }

    @Override
    public Void visit(IncludeBlockNode node) {
        String included = stripQuotes(node.getTemplateName());
        File target = new File(templateDir, included);
        if (!target.exists()) {
            warning("{% include \"" + included + "\" %} references a file that does not "
                    + "exist: " + target.getPath(), node.getLine(), node.getColumn());
        }
        sawJinjaBeforeExtends = true;
        return super.visit(node);
    }

    @Override
    public Void visit(ImportBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% import \"" + target + "\" ... %} references a file that does not exist: " + targetFile.getPath(), node.getLine(), node.getColumn());
        }
        sawJinjaBeforeExtends = true;
        return super.visit(node);
    }

    @Override
    public Void visit(FromImportBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% from \"" + target + "\" ... %} references a file that does not exist: " + targetFile.getPath(), node.getLine(), node.getColumn());
        }
        sawJinjaBeforeExtends = true;
        return super.visit(node);
    }

    // ── Expressions ──────────────────────────────────────────────────────────

    @Override
    public Void visit(FilterExpressionNode node) {
        if (!KNOWN_FILTERS.contains(node.getFilterName())) {
            warning("Unknown Jinja2 filter '" + node.getFilterName()
                    + "' — not in known filter list", node.getLine(), node.getColumn());
        }
        return super.visit(node);
    }

    @Override
    public Void visit(VariableNode node) {
        checkName(node.getName(), node.getLine(), node.getColumn());
        return super.visit(node);
    }

    @Override
    public Void visit(AttributeAccessNode node) {
        // Skip checking attributes of builtin Jinja variables like 'loop'
        if (node.getObject() instanceof VariableNode varNode && "loop".equals(varNode.getName())) {
            return super.visit(node);
        }

        if (!allMockDataKeys.isEmpty()) {
            if (!allMockDataKeys.contains(node.getAttribute())) {
                error("attribute '" + node.getAttribute() + "' was not found in any extracted Python mock data dictionary", node.getLine(), node.getColumn());
            }
        }
        return super.visit(node);
    }

    @Override
    public Void visit(IndexAccessNode node) {
        if (node.getIndex() instanceof ast.template.jinja.expressions.literals.NumberLiteralNode numNode) {
            int index = (int) numNode.getValue();
            if (maxListSize > 0 && (index < 0 || index >= maxListSize)) {
                error("Index out of bounds: maximum list size in mock data is " + maxListSize + ", but index " + index + " was requested", node.getLine(), node.getColumn());
            }
        }
        return super.visit(node);
    }

    /**
     * Checks 5 and 11. A name is legitimate when it comes from render_template,
     * {% set %}, an enclosing {% for %} or {% with %}, or is a Jinja/Flask
     * builtin — anything else was never supplied to this template.
     */
    
    @Override
    public Void visit(CallExpressionNode node) {
        
        if (node.getCallee() instanceof VariableNode varNode) {
            if ("url_for".equals(varNode.getName())) {
                boolean isStatic = false;
                if (node.getArgumentCount() > 0 && node.getArgument(0) instanceof StringLiteralNode) {
                    StringLiteralNode str = (StringLiteralNode) node.getArgument(0);
                    if ("static".equals(stripQuotes(str.getValue()))) {
                        isStatic = true;
                    }
                }
                if (isStatic) {
                    for (int i = 0; i < node.getArgumentCount(); i++) {
                        if ("filename".equals(node.getKeywordName(i)) && node.getArgument(i) instanceof StringLiteralNode) {
                            StringLiteralNode str = (StringLiteralNode) node.getArgument(i);
                            String filename = stripQuotes(str.getValue());
                            File targetFile = new File(new File(templateDir).getParentFile(), "static/" + filename);
                            if (!targetFile.exists()) {
                                error("url_for('static', filename='" + filename + "') references a file that does not exist: " + targetFile.getPath(), node.getLine(), node.getColumn());
                            }
                        }
                    }
                }
            }
        }
        return super.visit(node);
    }

    private void checkName(String name, int line, int col) {
        if (name == null || name.isEmpty()) return;

        if (expiredLoopVars.contains(name) && !activeLoopVars.contains(name)) {
            warning("Variable '" + name + "' is a for-loop variable used outside "
                    + "its for-loop scope", line, col);
            expiredLoopVars.remove(name);
            return;
        }

        if (BUILTIN_NAMES.contains(name)) return;
        if (pythonContextVars.contains(name)) return;
        if (setVarLines.containsKey(name)) return;
        if (activeLoopVars.contains(name)) return;
        if (activeWithVars.contains(name)) return;

        error("template variable '" + name + "' is not passed from render_template",
                line, col);
    }

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

