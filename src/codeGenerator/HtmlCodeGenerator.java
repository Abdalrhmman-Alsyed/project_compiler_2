package codeGenerator;

import ast.template.TemplateNode;
import ast.template.TemplateRootNode;
import ast.template.css.*;
import ast.template.html.*;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.BooleanLiteralNode;
import ast.template.jinja.expressions.literals.NoneLiteralNode;
import ast.template.jinja.expressions.literals.NumberLiteralNode;
import ast.template.jinja.expressions.literals.StringLiteralNode;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import semantic.Generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Walks a Jinja AST with bound Python context data and writes finished HTML
 * files. {@code {% extends %}} is resolved against sibling templates; Flask
 * {@code url_for} calls become relative links in {@code output/}.
 */
public class HtmlCodeGenerator {

    private static final Set<String> VOID_TAGS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    );

    /** Tags that stay on one line with their text (no nested indent). */
    private static final Set<String> INLINE_TAGS = Set.of(
            "a", "span", "strong", "em", "b", "i", "u", "small", "label",
            "button", "img", "input", "br", "meta", "link", "title",
            "code", "abbr", "time", "sub", "sup"
    );

    private static final String INDENT = "    ";

    private final Path templateDir;
    private final Path outputDir;
    private final Map<String, TemplateNode> templates = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();

    private final Deque<Map<String, Object>> scopes = new ArrayDeque<>();
    private Map<String, BlockBlockNode> blockOverrides = Map.of();
    private int indentLevel = 0;

    public HtmlCodeGenerator(Path templateDir, Path outputDir) {
        this.templateDir = templateDir;
        this.outputDir = outputDir;
    }

    public void loadTemplates() throws IOException {
        templates.clear();
        if (!Files.isDirectory(templateDir)) {
            throw new IOException("Template directory not found: " + templateDir);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateDir, "*.jinja")) {
            for (Path file : stream) {
                templates.put(file.getFileName().toString(), parseTemplate(file));
            }
        }
        log.add("Loaded " + templates.size() + " template(s) from " + templateDir);
    }

    public void putTemplate(String name, TemplateNode ast) {
        templates.put(name, ast);
    }

    /**
     * Builds the per-template context from bound Python literals.
     * Unresolved names (e.g. {@code product=product}) take the first product
     * dict so a static detail page can still be generated.
     */
    public Map<String, Object> contextFor(String templateName,
                                          Generator generator,
                                          Map<String, Object> bound) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Set<String> names = generator.getTemplateContextVars()
                .getOrDefault(templateName, Set.of());
        Map<String, String> sources = generator.getTemplateContextSources()
                .getOrDefault(templateName, Map.of());
        for (String name : names) {
            Object value = bound != null ? bound.get(name) : null;
            if (value == null && bound != null) {
                String src = sources.get(name);
                if (src != null) value = bound.get(src);
            }
            if (value == null) {
                value = firstProduct(bound);
            }
            if (value != null) ctx.put(name, value);
        }
        return ctx;
    }

    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Path> generateRenderedPages(Generator generator, Map<String, Object> bound)
            throws IOException {
        return generateRenderedPages(generator, bound,
                "compiler pipeline (Main) -- first create of HTML pages", true);
    }

    public List<Path> generateRenderedPages(Generator generator, Map<String, Object> bound,
                                            String reason) throws IOException {
        return generateRenderedPages(generator, bound, reason, true);
    }

    /**
     * @param resetLog {@code true} on first create (wipe the log file);
     *                 {@code false} when the UI changes data (append).
     */
    public List<Path> generateRenderedPages(Generator generator, Map<String, Object> bound,
                                            String reason, boolean resetLog) throws IOException {
        Files.createDirectories(outputDir);
        log.clear();
        logGenerationEvent(reason, resetLog);
        List<Path> written = new ArrayList<>();
        for (String templateName : generator.getTemplateContextVars().keySet()) {
            Map<String, Object> ctx = contextFor(templateName, generator, bound);
            Path file = write(templateName, ctx);
            written.add(file);
        }
        copyStaticAssets();
        writeGenerationLog(resetLog);
        return written;
    }

    /**
     * First run of {@code Main} (or a standalone server start) writes
     * {@code Generated} and wipes the log. Later UI-driven writes while the
     * server is up append {@code REGENERATION} plus the operation that
     * triggered it.
     */
    public void logGenerationEvent(String reason, boolean initial) {
        String why = (reason == null || reason.isBlank()) ? "unspecified" : reason.trim();
        String time = LocalDateTime.now().format(LOG_TIME);
        if (initial) {
            log.add("Generated at " + time);
            log.add("Operation: " + why);
        } else {
            log.add("REGENERATION at " + time);
            log.add("Caused by: " + why);
        }
    }

    public Path write(String templateName, Map<String, Object> context) throws IOException {
        Files.createDirectories(outputDir);
        String html = render(templateName, context);
        String stem = stem(templateName);
        Path out = outputDir.resolve(stem + ".html");
        Files.writeString(out, html, StandardCharsets.UTF_8);
        log.add("Wrote " + out.toAbsolutePath().normalize()
                + "  (context keys: " + context.keySet() + ")");
        return out;
    }

    public Path write(String templateName, Map<String, Object> context, String reason)
            throws IOException {
        log.clear();
        logGenerationEvent(reason, false);
        Path out = write(templateName, context);
        writeGenerationLog(false);
        return out;
    }

    public String render(String templateName, Map<String, Object> context) {
        TemplateNode ast = templates.get(templateName);
        if (ast == null) {
            throw new IllegalArgumentException("Unknown template: " + templateName);
        }
        scopes.clear();
        scopes.push(context != null ? new LinkedHashMap<>(context) : new LinkedHashMap<>());
        try {
            return renderTemplate(ast, new LinkedHashMap<>());
        } finally {
            scopes.clear();
            blockOverrides = Map.of();
            indentLevel = 0;
        }
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  HTML CODE GENERATOR -> " + outputDir);
        System.out.println("=".repeat(60));
        if (log.isEmpty()) {
            System.out.println("  No files generated.");
            return;
        }
        for (String line : log) {
            System.out.println("  " + line);
        }
    }

    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    public void writeGenerationLog() throws IOException {
        writeGenerationLog(false);
    }

    public void writeGenerationLog(boolean reset) throws IOException {
        Path compilerOutput = Paths.get("compiler_output");
        Files.createDirectories(compilerOutput);
        Path logFile = compilerOutput.resolve("generation_log.txt");
        String header = "HTML code generation log\n" + "=".repeat(60) + "\n";
        StringBuilder body = new StringBuilder();
        if (log.isEmpty()) {
            body.append("(empty)\n");
        } else {
            body.append('\n');
            body.append("-".repeat(60)).append('\n');
            for (String line : log) {
                body.append(line).append('\n');
            }
        }
        if (reset) {
            Files.writeString(logFile, header + body, StandardCharsets.UTF_8);
        } else {
            if (!Files.exists(logFile)) {
                Files.writeString(logFile, header, StandardCharsets.UTF_8);
            }
            Files.writeString(logFile, body.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.out.println("  generation_log.txt -> " + logFile.toAbsolutePath().normalize()
                + (reset ? " (reset)" : " (append)"));
    }

    public Path getOutputDir() {
        return outputDir;
    }

    // ── extends / blocks ──────────────────────────────────────────────────

    private String renderTemplate(TemplateNode ast, Map<String, BlockBlockNode> inherited) {
        ExtendsBlockNode ext = findExtends(ast);
        Map<String, BlockBlockNode> mine = collectBlocks(ast);
        mine.putAll(inherited);
        if (ext != null) {
            String parentName = stripQuotes(ext.getTemplateName());
            TemplateNode parent = templates.get(parentName);
            if (parent == null) {
                log.add("WARNING: missing parent template " + parentName);
                return renderNode(ast);
            }
            Map<String, BlockBlockNode> saved = blockOverrides;
            blockOverrides = mine;
            try {
                return renderTemplate(parent, mine);
            } finally {
                blockOverrides = saved;
            }
        }
        Map<String, BlockBlockNode> saved = blockOverrides;
        blockOverrides = mine;
        try {
            return renderNode(ast);
        } finally {
            blockOverrides = saved;
        }
    }

    private ExtendsBlockNode findExtends(TemplateNode node) {
        if (node instanceof ExtendsBlockNode e) return e;
        for (TemplateNode child : node.getChildren()) {
            ExtendsBlockNode found = findExtends(child);
            if (found != null) return found;
        }
        return null;
    }

    private Map<String, BlockBlockNode> collectBlocks(TemplateNode node) {
        Map<String, BlockBlockNode> blocks = new LinkedHashMap<>();
        collectBlocks(node, blocks);
        return blocks;
    }

    private void collectBlocks(TemplateNode node, Map<String, BlockBlockNode> into) {
        if (node instanceof BlockBlockNode block) {
            into.putIfAbsent(block.getBlockName(), block);
        }
        for (TemplateNode child : node.getChildren()) {
            collectBlocks(child, into);
        }
    }

    // ── node rendering ────────────────────────────────────────────────────

    private String renderNode(TemplateNode node) {
        if (node == null) return "";
        if (node instanceof TemplateRootNode n) return renderChildren(n.getDocuments());
        if (node instanceof DoctypeNode n) return n.getDoctype() + "\n";
        if (node instanceof HTMLDocumentNode n) return renderHtmlDocument(n);
        if (node instanceof HTMLNormalElementNode n) return renderNormal(n);
        if (node instanceof HTMLVoidElementNode n) return renderVoid(n);
        if (node instanceof HTMLSelfClosingElementNode n) return renderSelfClosing(n);
        if (node instanceof HTMLTextNode n) return prettyText(n.getText());
        if (node instanceof HTMLAttributeTextNode n) return n.getText() != null ? n.getText() : "";
        if (node instanceof HTMLClosingTagNode) return "";
        if (node instanceof JinjaExpressionNode n) return escape(stringify(eval(n.getExpression())));
        if (node instanceof ExtendsBlockNode) return "";
        if (node instanceof BlockBlockNode n) return renderBlock(n);
        if (node instanceof IfBlockNode n) return renderIf(n);
        if (node instanceof ForBlockNode n) return renderFor(n);
        if (node instanceof ElseBlockNode n) return renderChildren(n.getContent());
        if (node instanceof ElifBlockNode n) return renderChildren(n.getContent());
        if (node instanceof SetBlockNode n) {
            put(n.getVariable(), eval(n.getExpression()));
            return "";
        }
        if (node instanceof WithBlockNode n) return renderWith(n);
        if (node instanceof IncludeBlockNode n) return renderInclude(n);
        if (node instanceof ImportBlockNode) return "";
        if (node instanceof FromImportBlockNode) return "";
        if (node instanceof GenericBlockNode n) return renderChildren(n.getContent());
        if (node instanceof CSSStyleNode n) return renderCssStyle(n);
        if (node instanceof CSSRuleNode n) return renderCssRule(n);
        if (node instanceof CSSSelectorNode n) return n.getSelector() != null ? n.getSelector() : "";
        if (node instanceof CSSDeclarationNode n)
            return n.getProperty() + ": " + n.getValueText() + ";";
        if (node instanceof CSSValueNode n) return n.getValue() != null ? n.getValue() : "";
        if (node instanceof CSSAttributeNode n) {
            return n.getValue() != null ? n.getName() + ": " + n.getValue() : n.getName();
        }
        if (node instanceof ExpressionNode e) return escape(stringify(eval(e)));
        return renderChildren(node.getChildren());
    }

    private String renderCssStyle(CSSStyleNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indentLine("<style" + renderCssAttributes(node.getAttributes()) + ">"));
        indentLevel++;
        for (CSSRuleNode rule : node.getRules()) {
            sb.append(renderCssRule(rule));
        }
        indentLevel--;
        sb.append(indentLine("</style>"));
        return sb.toString();
    }

    private String renderCssAttributes(List<CSSAttributeNode> attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CSSAttributeNode attr : attrs) {
            sb.append(' ').append(attr.getName());
            if (attr.getValue() != null) {
                sb.append("=\"").append(escapeAttr(attr.getValue())).append("\"");
            }
        }
        return sb.toString();
    }

    private String renderChildren(List<TemplateNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TemplateNode child : nodes) sb.append(renderNode(child));
        return sb.toString();
    }

    private String prettyText(String text) {
        if (text == null || text.isBlank()) return "";
        return text.strip();
    }

    private String indentLine(String content) {
        return INDENT.repeat(indentLevel) + content + "\n";
    }

    private boolean isInline(String tag) {
        return tag != null && INLINE_TAGS.contains(tag.toLowerCase(Locale.ROOT));
    }

    private String renderHtmlDocument(HTMLDocumentNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indentLine("<html" + renderAttributes(node.getAttributes()) + ">"));
        indentLevel++;
        sb.append(renderChildren(node.getContent()));
        indentLevel--;
        sb.append(indentLine("</html>"));
        return sb.toString();
    }

    private String renderNormal(HTMLNormalElementNode node) {
        String tag = node.getTagName();
        String open = "<" + tag + renderAttributes(node.getAttributes()) + ">";
        if (VOID_TAGS.contains(tag.toLowerCase(Locale.ROOT))) {
            return indentLine(open);
        }
        if (isInline(tag)) {
            return indentLine(open + renderChildren(node.getContent()) + "</" + tag + ">");
        }
        indentLevel++;
        String inner = renderChildren(node.getContent());
        indentLevel--;
        if (!inner.contains("\n")) {
            return indentLine(open + inner + "</" + tag + ">");
        }
        return indentLine(open) + inner + indentLine("</" + tag + ">");
    }

    private String renderVoid(HTMLVoidElementNode node) {
        return indentLine("<" + node.getTagName() + renderAttributes(node.getAttributes()) + ">");
    }

    private String renderSelfClosing(HTMLSelfClosingElementNode node) {
        return indentLine("<" + node.getTagName() + renderAttributes(node.getAttributes()) + " />");
    }

    private String renderAttributes(List<HTMLAttributeNode> attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (HTMLAttributeNode attr : attrs) {
            sb.append(' ').append(attr.getName());
            if (attr.isBoolean()) continue;
            sb.append("=\"");
            for (TemplateNode part : attr.getValueParts()) {
                if (part instanceof HTMLAttributeTextNode text) {
                    sb.append(escapeAttr(text.getText()));
                } else if (part instanceof JinjaExpressionNode expr) {
                    sb.append(escapeAttr(stringify(eval(expr.getExpression()))));
                } else {
                    sb.append(escapeAttr(renderNode(part)));
                }
            }
            sb.append('"');
        }
        return sb.toString();
    }

    private String renderBlock(BlockBlockNode node) {
        String blockName = node.getBlockName();
        BlockBlockNode override = blockOverrides.get(blockName);
        if (override != null && override != node) {
            blockOverrides.remove(blockName);
            try {
                return renderChildren(override.getContent());
            } finally {
                blockOverrides.put(blockName, override);
            }
        }
        return renderChildren(node.getContent());
    }

    private String renderIf(IfBlockNode node) {
        if (isTruthy(eval(node.getCondition()))) {
            return renderChildren(node.getContent());
        }
        for (ElifBlockNode elif : node.getElifBlocks()) {
            if (isTruthy(eval(elif.getCondition()))) {
                return renderChildren(elif.getContent());
            }
        }
        if (node.hasElseBlock()) {
            return renderChildren(node.getElseBlock().getContent());
        }
        return "";
    }

    private String renderFor(ForBlockNode node) {
        Object iterable = eval(node.getIterable());
        List<?> items = asList(iterable);
        if (items.isEmpty()) {
            return node.hasElseBlock() ? renderChildren(node.getElseBlock().getContent()) : "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Object item : items) {
            pushScope();
            put(node.getVariable(), item);
            put("loop", loopInfo(i, items.size()));
            sb.append(renderChildren(node.getContent()));
            popScope();
            i++;
        }
        return sb.toString();
    }

    private String renderWith(WithBlockNode node) {
        Object value = eval(node.getExpression());
        pushScope();
        if (node.hasVariable()) put(node.getVariable(), value);
        String html = renderChildren(node.getContent());
        popScope();
        return html;
    }

    private String renderInclude(IncludeBlockNode node) {
        String name = stripQuotes(node.getTemplateName());
        TemplateNode included = templates.get(name);
        if (included == null) {
            log.add("WARNING: {% include %} missing " + name);
            return "";
        }
        return renderTemplate(included, Map.of());
    }

    private String renderCssRule(CSSRuleNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indentLine(node.getSelectorText() + " {"));
        indentLevel++;
        for (CSSDeclarationNode decl : node.getDeclarations()) {
            sb.append(indentLine(decl.getProperty() + ": " + decl.getValueText() + ";"));
        }
        indentLevel--;
        sb.append(indentLine("}"));
        return sb.toString();
    }

    // ── expression evaluation ─────────────────────────────────────────────

    private Object eval(ExpressionNode expr) {
        if (expr == null) return null;
        if (expr instanceof VariableNode v) return lookup(v.getName());
        if (expr instanceof StringLiteralNode s) return s.getUnquotedValue();
        if (expr instanceof NumberLiteralNode n) return n.getValue();
        if (expr instanceof BooleanLiteralNode b) return b.getValue();
        if (expr instanceof NoneLiteralNode) return null;
        if (expr instanceof AttributeAccessNode a) return evalAttribute(a);
        if (expr instanceof IndexAccessNode i) return evalIndex(i);
        if (expr instanceof CallExpressionNode c) return evalCall(c);
        if (expr instanceof FilterExpressionNode f) return evalFilter(f);
        if (expr instanceof UnaryExpressionNode u) return evalUnary(u);
        if (expr instanceof BinaryExpressionNode b) return evalBinary(b);
        if (expr instanceof ListExpressionNode l) {
            List<Object> out = new ArrayList<>();
            for (ExpressionNode e : l.getElements()) out.add(eval(e));
            return out;
        }
        if (expr instanceof DictExpressionNode) return Map.of();
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object evalAttribute(AttributeAccessNode node) {
        Object obj = eval(node.getObject());
        String attr = node.getAttribute();
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(attr);
            if (val == null) val = ((Map<?, ?>) map).get(attr);
            return val;
        }
        if ("length".equals(attr) || "size".equals(attr)) {
            if (obj instanceof Collection<?> col) return col.size();
            if (obj instanceof String s) return s.length();
        }
        return null;
    }

    private Object evalIndex(IndexAccessNode node) {
        Object obj = eval(node.getObject());
        Object idx = eval(node.getIndex());
        if (obj instanceof List<?> list && idx instanceof Number n) {
            int i = n.intValue();
            if (i >= 0 && i < list.size()) return list.get(i);
        }
        if (obj instanceof Map<?, ?> map) {
            return map.get(String.valueOf(idx));
        }
        return null;
    }

    private Object evalCall(CallExpressionNode node) {
        if (node.getCallee() instanceof VariableNode v && "url_for".equals(v.getName())) {
            return evalUrlFor(node);
        }
        if (node.getCallee() instanceof VariableNode v && "range".equals(v.getName())) {
            return evalRange(node);
        }
        return null;
    }

    private String evalUrlFor(CallExpressionNode node) {
        String endpoint = "";
        if (node.getArgumentCount() > 0) {
            endpoint = stringify(eval(node.getArgument(0)));
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        for (int i = 0; i < node.getArgumentCount(); i++) {
            String kw = node.getKeywordName(i);
            if (kw != null) kwargs.put(kw, eval(node.getArgument(i)));
        }
        if ("static".equals(endpoint)) {
            Object file = kwargs.get("filename");
            String name = file != null ? stringify(file) : "";
            if (name.endsWith(".css") && !name.contains("/")) return "/style.css";
            return "/static/" + name;
        }
        return switch (endpoint) {
            case "product_list" -> "/";
            case "add_product" -> "/add";
            case "product_detail" -> {
                Object id = kwargs.get("product_id");
                yield id != null ? "/products/" + stringify(id) : "/products";
            }
            case "delete_product" -> {
                Object id = kwargs.get("product_id");
                yield id != null ? "/delete/" + stringify(id) : "/";
            }
            default -> "/" + endpoint;
        };
    }

    private Object evalRange(CallExpressionNode node) {
        int start = 0, stop = 0, step = 1;
        if (node.getArgumentCount() == 1 && eval(node.getArgument(0)) instanceof Number n) {
            stop = n.intValue();
        } else if (node.getArgumentCount() >= 2) {
            Object a = eval(node.getArgument(0));
            Object b = eval(node.getArgument(1));
            if (a instanceof Number na) start = na.intValue();
            if (b instanceof Number nb) stop = nb.intValue();
            if (node.getArgumentCount() >= 3 && eval(node.getArgument(2)) instanceof Number ns) {
                step = ns.intValue();
            }
        }
        List<Integer> out = new ArrayList<>();
        if (step == 0) return out;
        if (step > 0) for (int i = start; i < stop; i += step) out.add(i);
        else for (int i = start; i > stop; i += step) out.add(i);
        return out;
    }

    private Object evalFilter(FilterExpressionNode node) {
        Object input = eval(node.getInput());
        String name = node.getFilterName();
        if (name == null) return input;
        return switch (name) {
            case "length", "count" -> {
                if (input instanceof Collection<?> c) yield c.size();
                if (input instanceof String s) yield s.length();
                yield 0;
            }
            case "lower" -> stringify(input).toLowerCase(Locale.ROOT);
            case "upper" -> stringify(input).toUpperCase(Locale.ROOT);
            case "safe" -> input;
            default -> input;
        };
    }

    private Object evalUnary(UnaryExpressionNode node) {
        Object v = eval(node.getOperand());
        String op = node.getOperator();
        if ("not".equalsIgnoreCase(op) || "!".equals(op)) return !isTruthy(v);
        if ("-".equals(op) && v instanceof Number n) return -n.doubleValue();
        if ("+".equals(op)) return v;
        return v;
    }

    private Object evalBinary(BinaryExpressionNode node) {
        Object left = eval(node.getLeft());
        String op = node.getOperator();
        if ("and".equals(op)) return isTruthy(left) ? eval(node.getRight()) : left;
        if ("or".equals(op)) return isTruthy(left) ? left : eval(node.getRight());
        Object right = eval(node.getRight());
        if ("==".equals(op) || "eq".equals(op)) return Objects.equals(stringify(left), stringify(right));
        if ("!=".equals(op) || "ne".equals(op)) return !Objects.equals(stringify(left), stringify(right));
        
        if (left instanceof Number l && right instanceof Number r) {
            double lv = l.doubleValue();
            double rv = r.doubleValue();
            switch (op) {
                case ">": case "gt": return lv > rv;
                case ">=": case "ge": return lv >= rv;
                case "<": case "lt": return lv < rv;
                case "<=": case "le": return lv <= rv;
            }
        }
        
        if ("in".equals(op)) {
            if (right instanceof Collection<?> c) return c.contains(left);
            if (right instanceof String s) return s.contains(stringify(left));
            if (right instanceof Map<?, ?> m) return m.containsKey(left) || m.containsKey(stringify(left));
            return false;
        }
        return left;
    }

    // ── context / helpers ─────────────────────────────────────────────────

    private Object lookup(String name) {
        if (name == null) return null;
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    private void put(String name, Object value) {
        if (name == null || scopes.isEmpty()) return;
        scopes.peek().put(name, value);
    }

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        if (scopes.size() > 1) scopes.pop();
    }

    private static Map<String, Object> loopInfo(int index, int size) {
        Map<String, Object> loop = new LinkedHashMap<>();
        loop.put("index", index + 1);
        loop.put("index0", index);
        loop.put("first", index == 0);
        loop.put("last", index == size - 1);
        loop.put("length", size);
        return loop;
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof Collection<?> col) return new ArrayList<>(col);
        if (value == null) return List.of();
        return List.of(value);
    }

    private static Object firstProduct(Map<String, Object> bound) {
        if (bound == null) return null;
        for (String key : List.of("products", "PRODUCTS_BASE_DATA")) {
            Object v = bound.get(key);
            if (v instanceof List<?> list && !list.isEmpty()) return list.get(0);
        }
        for (Object v : bound.values()) {
            if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                return list.get(0);
            }
        }
        return null;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof Collection<?> c) return !c.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof Double d && d == d.intValue()) {
            return Integer.toString(d.intValue());
        }
        return String.valueOf(value);
    }

    private static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return escape(s).replace("\"", "&quot;");
    }

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String stem(String templateName) {
        int dot = templateName.lastIndexOf('.');
        return dot >= 0 ? templateName.substring(0, dot) : templateName;
    }

    private TemplateNode parseTemplate(Path file) throws IOException {
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(CharStreams.fromPath(file));
        FlaskTemplateParser parser = new FlaskTemplateParser(new CommonTokenStream(lexer));
        FlaskTemplateParser.TemplateRootContext tree =
                (FlaskTemplateParser.TemplateRootContext) parser.template();
        return new TemplateASTBuilder().visitTemplateRoot(tree);
    }

    private void copyStaticAssets() throws IOException {
        Path staticDir = templateDir.resolveSibling("static");
        if (!Files.isDirectory(staticDir)) return;
        Path css = staticDir.resolve("style.css");
        if (Files.isRegularFile(css)) {
            Files.copy(css, outputDir.resolve("style.css"), StandardCopyOption.REPLACE_EXISTING);
            log.add("Copied style.css");
        }
        Path destStatic = outputDir.resolve("static");
        Files.createDirectories(destStatic);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(staticDir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    Files.copy(file, destStatic.resolve(file.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
