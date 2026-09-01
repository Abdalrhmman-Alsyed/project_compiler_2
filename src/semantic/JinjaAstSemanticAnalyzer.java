package semantic;

import ast.template.TemplateNode;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.StringLiteralNode;
import ast.visitors.TemplateBaseASTVisitor;

import java.io.File;
import java.util.*;

import symbolTable.scopes.Scope;
import symbolTable.scopes.ScopeType;
import symbolTable.symbols.SymbolKind;
import symbolTable.symbols.Symbol;
import symbolTable.JinjaSymbolTable;

/**
 * The 8 Jinja2 semantic checks:
 * 1. Duplicate block name in the same template
 * 2. {% extends %} used more than once
 * 3. Template extends itself (circular dependency) or references non-existent file
 * 4. HTML tag mismatch
 * 5. Template variable never passed from render_template
 * 6. Attribute not found in mock data
 * 7. Index out of bounds
 * 8. url_for('static') points to non-existent file
 */
public class JinjaAstSemanticAnalyzer extends TemplateBaseASTVisitor<Void> {

    private final String templateName;
    private final String templateDir;
    private final Set<String> pythonContextVars;
    private final Map<String, Object> mockData;
    private final Set<String> allMockDataKeys = new LinkedHashSet<>();
    private final JinjaSymbolTable symbolTable;
    private final Map<String, Integer> visitedBlocks = new LinkedHashMap<>();

    private final List<SemanticError> errors = new ArrayList<>();
    private final List<SemanticError> warnings = new ArrayList<>();

    private int extendsCount = 0;

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
                                    Set<String> pythonContextVars, Map<String, Object> mockData,
                                    JinjaSymbolTable symbolTable) {
        this.templateName = templateName;
        this.templateDir = templateDir;
        this.pythonContextVars = pythonContextVars != null ? pythonContextVars : Set.of();
        this.mockData = mockData != null ? mockData : Map.of();
        this.symbolTable = symbolTable;
        collectKeys(this.mockData);
    }

    public JinjaAstSemanticAnalyzer(String templateName, String templateDir, Set<String> pythonContextVars, JinjaSymbolTable symbolTable) {
        this(templateName, templateDir, pythonContextVars, null, symbolTable);
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

    public void saveReportToFile(String filePath) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filePath, true))) {
            writer.println("\n" + "=".repeat(60));
            writer.println("  تحليل الدلالات لقوالب جينجا2 (V2): " + templateName + "  (8 فحص)");
            writer.println("=".repeat(60));

            if (errors.isEmpty() && warnings.isEmpty()) {
                writer.println("  لم يتم العثور على أي مشاكل دلالية.");
            } else {
                if (!errors.isEmpty()) {
                    writer.println("\n  الأخطاء (" + errors.size() + "):");
                    errors.forEach(e -> writer.println("  " + e));
                }
                if (!warnings.isEmpty()) {
                    writer.println("\n  التحذيرات (" + warnings.size() + "):");
                    warnings.forEach(w -> writer.println("  " + w));
                }
            }
            writer.printf("%n  الملخص: %d خطأ، %d تحذير = %d إجمالي المشاكل%n",
                    errors.size(), warnings.size(), errors.size() + warnings.size());
        } catch (java.io.IOException e) {
            System.err.println("Failed to write semantic report: " + e.getMessage());
        }
    }

    private final Map<Scope, Integer> childIndexMap = new HashMap<>();
    private Scope currentScope;

    private void pushScope() {
        if (currentScope == null) {
            currentScope = symbolTable.getGlobalScope();
        } else {
            List<Scope> children = symbolTable.getChildScopes(currentScope);
            int index = childIndexMap.getOrDefault(currentScope, 0);
            if (index < children.size()) {
                currentScope = children.get(index);
                childIndexMap.put(currentScope.getParent(), index + 1);
            }
        }
    }

    private void popScope() {
        if (currentScope != null && currentScope.getParent() != null) {
            currentScope = currentScope.getParent();
        }
    }

    @Override
    public Void visit(ast.template.TemplateRootNode node) {
        pushScope(); // الدخول للنطاق العام
        super.visit(node);
        return null;
    }

    public List<SemanticError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<SemanticError> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  JINJA2 SEMANTIC ANALYSIS (V2): " + templateName + "  (8 checks)");
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

    // ── الكتل (Blocks) ───────────────────────────────────────────────────────

    @Override
    public Void visit(ast.template.html.HTMLNormalElementNode node) {
        String opening = node.getTagName();
        String closing = node.getClosingTagName();
        if (closing != null && !opening.equals(closing)) {
            error("عدم تطابق في التاغ (HTML): التاغ المفتوح <" + opening + "> لا يطابق التاغ المغلق </" + closing + ">", node.getClosingLine(), node.getClosingColumn());
        }
        return super.visit(node);
    }

    @Override
    public Void visit(BlockBlockNode node) {
        String name = node.getBlockName();
        if (visitedBlocks.containsKey(name)) {
            error("الكتلة (Block) '" + name + "' تم تعريفها أكثر من مرة (أول مرة في السطر "
                    + visitedBlocks.get(name) + ")", node.getLine(), node.getColumn());
        } else {
            visitedBlocks.put(name, node.getLine());
        }

        
        pushScope();
        super.visit(node);
        popScope();
        return null;
    }

    @Override
    public Void visit(ExtendsBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        int line = node.getLine(), col = node.getColumn();

        if (++extendsCount > 1) {
            error("تم استخدام {% extends %} أكثر من مرة في القالب '" + templateName + "'",
                    line, col);
        }
        if (templateName.equals(target)) {
            error("القالب '" + templateName + "' يمتد (extends) من نفسه — هذا استدعاء دائري (Circular dependency)",
                    line, col);
        }

        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% extends \"" + target + "\" %} يشير إلى ملف غير موجود: " + targetFile.getPath(), line, col);
        }

        return super.visit(node);
    }

    @Override
    public Void visit(ForBlockNode node) {
        String loopVar = node.getVariable();


        visitChild(node.getIterable());
        
        pushScope();
        for (TemplateNode c : node.getContent()) visitChild(c);
        popScope();

        if (node.hasElseBlock()) visitChild(node.getElseBlock());
        return null;
    }

    @Override
    public Void visit(WithBlockNode node) {

        visitChild(node.getExpression());

        pushScope();
        for (TemplateNode c : node.getContent()) visitChild(c);
        popScope();
        
        return null;
    }

    @Override
    public Void visit(SetBlockNode node) {
        String name = node.getVariable();
        int line = node.getLine(), col = node.getColumn();


        return super.visit(node);
    }

    @Override
    public Void visit(IncludeBlockNode node) {
        String included = stripQuotes(node.getTemplateName());
        File target = new File(templateDir, included);
        if (!target.exists()) {
            error("{% include \"" + included + "\" %} يشير إلى ملف غير موجود: "
                    + target.getPath(), node.getLine(), node.getColumn());
        }

        return super.visit(node);
    }

    @Override
    public Void visit(ImportBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% import \"" + target + "\" ... %} يشير إلى ملف غير موجود: " + targetFile.getPath(), node.getLine(), node.getColumn());
        }

        return super.visit(node);
    }

    @Override
    public Void visit(FromImportBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% from \"" + target + "\" ... %} يشير إلى ملف غير موجود: " + targetFile.getPath(), node.getLine(), node.getColumn());
        }

        return super.visit(node);
    }

    // ── التعابير (Expressions) ───────────────────────────────────────────────

    @Override
    public Void visit(FilterExpressionNode node) {
        return super.visit(node);
    }

    @Override
    public Void visit(VariableNode node) {
        checkName(node.getName(), node.getLine(), node.getColumn());
        return super.visit(node);
    }

    @Override
    public Void visit(AttributeAccessNode node) {
        // تخطي فحص خصائص المتغيرات المبنية مسبقاً في جينجا مثل 'loop'
        if (node.getObject() instanceof VariableNode varNode && "loop".equals(varNode.getName())) {
            return super.visit(node);
        }

        if (!allMockDataKeys.isEmpty()) {
            if (!allMockDataKeys.contains(node.getAttribute())) {
                error("السمة (Attribute) '" + node.getAttribute() + "' لم يتم العثور عليها في أي قاموس بيانات وهمية من بايثون", node.getLine(), node.getColumn());
            }
        }
        return super.visit(node);
    }

    @Override
    public Void visit(IndexAccessNode node) {
        if (node.getIndex() instanceof ast.template.jinja.expressions.literals.NumberLiteralNode numNode) {
            int index = (int) numNode.getValue();
            if (maxListSize > 0 && (index < 0 || index >= maxListSize)) {
                error("الفهرس خارج النطاق: الحد الأقصى لحجم القائمة هو " + maxListSize + "، ولكن تم طلب الفهرس " + index, node.getLine(), node.getColumn());
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
                                error("استدعاء url_for('static', filename='" + filename + "') يشير إلى ملف غير موجود: " + targetFile.getPath(), node.getLine(), node.getColumn());
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

        if (BUILTIN_NAMES.contains(name)) return;
        if (pythonContextVars.contains(name)) return;

        // فحص جدول الرموز إن كان متاحاً
        if (symbolTable != null && currentScope != null) {
            Symbol sym = currentScope.resolve(name);
            // رمز BLOCK ليس متغيراً ممرراً من Python — نتجاهله
            if (sym != null && sym.getKind() != SymbolKind.BLOCK) {
                if (sym.getLine() > line) {
                    error("متغير القالب '" + name + "' لم يتم تمريره من دالة render_template", line, col);
                }
                return;
            }
        }



        error("متغير القالب '" + name + "' لم يتم تمريره من دالة render_template",
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

