package semantic;

import ast.template.TemplateNode;
import ast.template.html.HTMLNormalElementNode;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.BooleanLiteralNode;
import ast.template.jinja.expressions.literals.NoneLiteralNode;
import ast.template.jinja.expressions.literals.NumberLiteralNode;
import ast.template.jinja.expressions.literals.StringLiteralNode;
import ast.visitors.TemplateBaseASTVisitor;

import java.io.File;
import java.util.*;

import symbolTable.scopes.Scope;
import symbolTable.scopes.ScopeType;
import symbolTable.symbols.SymbolKind;

/**
 * Jinja2 semantic checks:
 *   duplicate {% block %} name
 *   {% extends %} more than once or circular
 *   missing file in {% include %} / {% import %} / {% extends %}
 *   HTML opening/closing tag mismatch
 *   missing flask variable (never passed from render_template)
 *   attribute missing from mock data
 *   index out of bounds
 *   url_for('static') file not found
 *   scope error (for-loop variable used after the loop)
 *   type error: {% for %} iterable is not iterable
 */
public class JinjaAstSemanticAnalyzer extends TemplateBaseASTVisitor<Void> {

    private final String templateName;
    private final String templateDir;
    private final Set<String> pythonContextVars;
    private final Map<String, Object> mockData;
    private final Set<String> allMockDataKeys = new LinkedHashSet<>();
    private final symbolTable.JinjaSymbolTable symbolTable;
    private final Map<String, Integer> visitedBlocks = new LinkedHashMap<>();
    private final Set<String> reportedExpiredLoopVars = new LinkedHashSet<>();

    private final List<SemanticError> errors = new ArrayList<>();
    private final List<SemanticError> warnings = new ArrayList<>();

    private int extendsCount = 0;
    private int maxListSize = 0;

    private static final Set<String> BUILTIN_NAMES = Set.of(
            "loop", "range", "dict", "list", "cycler", "namespace", "lipsum", "joiner",
            "url_for", "get_flashed_messages", "request", "session", "g", "config",
            "self", "super", "true", "false", "none", "True", "False", "None"
    );

    public JinjaAstSemanticAnalyzer(String templateName, String templateDir,
                                    Set<String> pythonContextVars, Map<String, Object> mockData,
                                    symbolTable.JinjaSymbolTable symbolTable) {
        this.templateName = templateName;
        this.templateDir = templateDir;
        this.pythonContextVars = pythonContextVars != null ? pythonContextVars : Set.of();
        this.mockData = mockData != null ? mockData : Map.of();
        this.symbolTable = symbolTable;
        collectKeys(this.mockData);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public JinjaAstSemanticAnalyzer(String templateName, String templateDir, Set<String> pythonContextVars, symbolTable.JinjaSymbolTable symbolTable) {
        this(templateName, templateDir, pythonContextVars, null, symbolTable);
    }

    private void collectKeys(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key instanceof String s) allMockDataKeys.add(s);
            }
            for (Object val : map.values()) collectKeys(val);
        } else if (obj instanceof List<?> list) {
            if (list.size() > maxListSize) maxListSize = list.size();
            for (Object item : list) collectKeys(item);
        }
    }

    private void error(String m, int l, int c) {
        errors.add(new SemanticError(SemanticError.Severity.ERROR, m, l, c));
    }

    public void saveReportToFile(String filePath) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filePath, true))) {
            writer.println("\n" + "=".repeat(60));
            writer.println("  تحليل الدلالات لقوالب جينجا2: " + templateName);
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
        pushScope();
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
        System.out.println("  JINJA2 SEMANTIC ANALYSIS: " + templateName);
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

    @Override
    public Void visit(HTMLNormalElementNode node) {
        String opening = node.getTagName();
        String closing = node.getClosingTagName();
        if (closing != null && !opening.equals(closing)) {
            error("عدم تطابق في التاغ (HTML): التاغ المفتوح <" + opening
                    + "> لا يطابق التاغ المغلق </" + closing + ">",
                    node.getClosingLine(), node.getClosingColumn());
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
            error("{% extends \"" + target + "\" %} يشير إلى ملف غير موجود: " + targetFile.getPath(),
                    line, col);
        }
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
            error("{% import \"" + target + "\" ... %} يشير إلى ملف غير موجود: "
                    + targetFile.getPath(), node.getLine(), node.getColumn());
        }
        return super.visit(node);
    }

    @Override
    public Void visit(FromImportBlockNode node) {
        String target = stripQuotes(node.getTemplateName());
        File targetFile = new File(templateDir, target);
        if (!targetFile.exists()) {
            error("{% from \"" + target + "\" ... %} يشير إلى ملف غير موجود: "
                    + targetFile.getPath(), node.getLine(), node.getColumn());
        }
        return super.visit(node);
    }

    @Override
    public Void visit(ForBlockNode node) {
        visitChild(node.getIterable());
        checkIterable(node.getIterable());

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
    public Void visit(VariableNode node) {
        checkName(node.getName(), node.getLine(), node.getColumn());
        return super.visit(node);
    }

    @Override
    public Void visit(AttributeAccessNode node) {
        if (node.getObject() instanceof VariableNode varNode && "loop".equals(varNode.getName())) {
            return super.visit(node);
        }
        if (!allMockDataKeys.isEmpty() && node.getAttribute() != null
                && !allMockDataKeys.contains(node.getAttribute())) {
            error("السمة (Attribute) '" + node.getAttribute()
                    + "' لم يتم العثور عليها في أي قاموس بيانات وهمية من بايثون",
                    node.getLine(), node.getColumn());
        }
        return super.visit(node);
    }

    @Override
    public Void visit(IndexAccessNode node) {
        if (node.getIndex() instanceof NumberLiteralNode numNode) {
            int index = numNode.getValue().intValue();
            if (maxListSize > 0 && (index < 0 || index >= maxListSize)) {
                error("الفهرس خارج النطاق: الحد الأقصى لحجم القائمة هو " + maxListSize
                        + "، ولكن تم طلب الفهرس " + index, node.getLine(), node.getColumn());
            }
        }
        return super.visit(node);
    }

    @Override
    public Void visit(CallExpressionNode node) {
        if (node.getCallee() instanceof VariableNode varNode && "url_for".equals(varNode.getName())) {
            boolean isStatic = false;
            if (node.getArgumentCount() > 0 && node.getArgument(0) instanceof StringLiteralNode str
                    && "static".equals(stripQuotes(str.getValue()))) {
                isStatic = true;
            }
            if (isStatic) {
                for (int i = 0; i < node.getArgumentCount(); i++) {
                    if ("filename".equals(node.getKeywordName(i))
                            && node.getArgument(i) instanceof StringLiteralNode str) {
                        String filename = stripQuotes(str.getValue());
                        File targetFile = new File(new File(templateDir).getParentFile(), "static/" + filename);
                        if (!targetFile.exists()) {
                            error("استدعاء url_for('static', filename='" + filename
                                    + "') يشير إلى ملف غير موجود: " + targetFile.getPath(),
                                    node.getLine(), node.getColumn());
                        }
                    }
                }
            }
        }
        return super.visit(node);
    }

    private void checkIterable(ExpressionNode iterable) {
        if (iterable == null) return;
        if (!isProvablyNotIterable(iterable)) return;

        String typeName = describeNonIterable(iterable);
        error("Type Error: النوع '" + typeName + "' غير قابل للتكرار (Not iterable)",
                iterable.getLine(), iterable.getColumn());
    }

    private boolean isProvablyNotIterable(ExpressionNode expr) {
        if (expr instanceof NumberLiteralNode) return true;
        if (expr instanceof BooleanLiteralNode) return true;
        if (expr instanceof NoneLiteralNode) return true;
        if (expr instanceof ListExpressionNode) return false;
        if (expr instanceof DictExpressionNode) return false;
        if (expr instanceof StringLiteralNode) return false;

        if (expr instanceof VariableNode var) {
            String name = var.getName();
            if (name == null || !mockData.containsKey(name)) return false;
            Object val = mockData.get(name);
            return !isIterableValue(val);
        }
        return false;
    }

    private boolean isIterableValue(Object val) {
        if (val == null) return false;
        if (val instanceof String) return true;
        if (val instanceof Map) return true;
        if (val instanceof Iterable) return true;
        return val.getClass().isArray();
    }

    private String describeNonIterable(ExpressionNode expr) {
        if (expr instanceof NumberLiteralNode) return "NUMBER";
        if (expr instanceof BooleanLiteralNode) return "BOOL";
        if (expr instanceof NoneLiteralNode) return "NONE";
        if (expr instanceof VariableNode var) {
            Object val = mockData.get(var.getName());
            if (val == null) return "NONE";
            if (val instanceof Number) return "NUMBER";
            if (val instanceof Boolean) return "BOOL";
            return val.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }

    /**
     * A name is legitimate when it comes from render_template, {% set %},
     * an enclosing {% for %} or {% with %}, or is a Jinja/Flask builtin.
     */
    private void checkName(String name, int line, int col) {
        if (name == null || name.isEmpty()) return;

        if (BUILTIN_NAMES.contains(name)) return;
        if (pythonContextVars.contains(name)) return;

        if (symbolTable != null && currentScope != null) {
            symbolTable.symbols.Symbol sym = currentScope.resolve(name);
            if (sym != null && sym.getKind() != SymbolKind.BLOCK) {
                if (sym.getLine() > line) {
                    error("Missing Flask Variable: متغير القالب '" + name
                            + "' لم يتم تمريره من دالة render_template", line, col);
                }
                return;
            }
        }

        boolean wasLoopVar = false;
        if (symbolTable != null) {
            for (Scope s : symbolTable.getAllScopes()) {
                if (s.getScopeType() == ScopeType.FOR_LOOP && s.getSymbol(name) != null) {
                    wasLoopVar = true;
                    break;
                }
            }
        }

        if (wasLoopVar) {
            if (!reportedExpiredLoopVars.contains(name)) {
                error("Scope Error: المتغير '" + name
                        + "' هو متغير حلقة 'for' يتم استخدامه خارج النطاق الخاص بتلك الحلقة",
                        line, col);
                reportedExpiredLoopVars.add(name);
            }
            return;
        }

        error("Missing Flask Variable: متغير القالب '" + name
                + "' لم يتم تمريره من دالة render_template", line, col);
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
