package semantic;

import gen.FlaskPythonParser;
import gen.FlaskPythonParserBaseVisitor;

import java.util.*;

/**
 * Generator: walks the Python parse tree and extracts the variables that each
 * render_template() call passes to a Jinja2 template.
 *
 * For each keyword argument {@code products=PRODUCTS_BASE_DATA}:
 *   - {@code products} is the Jinja context name
 *   - {@code PRODUCTS_BASE_DATA} is the Python identifier holding the literal
 *
 * {@link #bind(Map)} copies those Python literals under the Jinja names so
 * MockDataExtractor and this class share the same values with no data-flow
 * tracker: one lookup of the identifier on the right-hand side.
 */
public class Generator extends FlaskPythonParserBaseVisitor<Void> {

    // template name → set of Jinja context variable names
    private final Map<String, Set<String>> templateContextVars = new LinkedHashMap<>();

    // template name → (Jinja name → Python identifier on the RHS)
    private final Map<String, Map<String, String>> templateContextSources = new LinkedHashMap<>();

    // State while we are inside a render_template(...) call
    private boolean inRenderTemplateCall = false;
    private String  currentTemplateName  = null;
    private int     argPosition          = 0;

    // ── Visit primary expressions to detect render_template calls ─────────
    @Override
    public Void visitPrimaryExpression(FlaskPythonParser.PrimaryExpressionContext ctx) {
        // Check if the atom is the identifier 'render_template'
        if (ctx.atom() instanceof FlaskPythonParser.IdAtomContext idCtx
                && "render_template".equals(idCtx.ID().getText())) {

            // Look for a call postfix (arguments in parentheses)
            for (var postfix : ctx.postfix()) {
                if (postfix instanceof FlaskPythonParser.CallExprContext callCtx) {
                    // Save outer state (support nested calls, just in case)
                    boolean savedIn     = inRenderTemplateCall;
                    String  savedName   = currentTemplateName;
                    int     savedArgPos = argPosition;

                    inRenderTemplateCall = true;
                    currentTemplateName  = null;
                    argPosition          = 0;

                    if (callCtx.argumentList() != null) {
                        visit(callCtx.argumentList());
                    }

                    inRenderTemplateCall = savedIn;
                    currentTemplateName  = savedName;
                    argPosition          = savedArgPos;
                    return null;
                }
            }
        }
        return super.visitPrimaryExpression(ctx);
    }

    // ── Positional argument: first one is the template name ───────────────
    @Override
    public Void visitPositionalArgument(FlaskPythonParser.PositionalArgumentContext ctx) {
        if (inRenderTemplateCall && argPosition == 0) {
            String text = ctx.expression().getText();
            if ((text.startsWith("'") && text.endsWith("'"))
                    || (text.startsWith("\"") && text.endsWith("\""))) {
                currentTemplateName = text.substring(1, text.length() - 1);
                templateContextVars.computeIfAbsent(currentTemplateName,
                        k -> new LinkedHashSet<>());
            }
        }
        argPosition++;
        return super.visitPositionalArgument(ctx);
    }

    // ── Keyword argument: name=value → Jinja name + optional Python identifier ─
    @Override
    public Void visitKeywordArgument(FlaskPythonParser.KeywordArgumentContext ctx) {
        if (inRenderTemplateCall && currentTemplateName != null) {
            String varName = ctx.ID().getText();
            templateContextVars.computeIfAbsent(currentTemplateName,
                    k -> new LinkedHashSet<>()).add(varName);

            String rhs = ctx.expression().getText();
            if (rhs.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                templateContextSources
                        .computeIfAbsent(currentTemplateName, k -> new LinkedHashMap<>())
                        .put(varName, rhs);
            }
        }
        argPosition++;
        return super.visitKeywordArgument(ctx);
    }

    // ── Public results ────────────────────────────────────────────────────
    public Map<String, Set<String>> getTemplateContextVars() {
        return Collections.unmodifiableMap(templateContextVars);
    }

    public Map<String, Map<String, String>> getTemplateContextSources() {
        return Collections.unmodifiableMap(templateContextSources);
    }

    /**
     * Aliases each Jinja context name to the MockDataExtractor value of the
     * Python identifier used in {@code render_template(..., name=IDENTIFIER)}.
     * Example: {@code products} → same list as {@code PRODUCTS_BASE_DATA}.
     */
    public Map<String, Object> bind(Map<String, Object> extracted) {
        Map<String, Object> bound = new LinkedHashMap<>();
        if (extracted != null) {
            bound.putAll(extracted);
        }
        if (extracted == null || extracted.isEmpty()) {
            return bound;
        }
        for (Map<String, String> sources : templateContextSources.values()) {
            for (Map.Entry<String, String> e : sources.entrySet()) {
                Object value = extracted.get(e.getValue());
                if (value != null) {
                    bound.put(e.getKey(), value);
                }
            }
        }
        return bound;
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  GENERATOR: Python → Jinja2 Context Bridge");
        System.out.println("=".repeat(60));

        if (templateContextVars.isEmpty()) {
            System.out.println("  No render_template() calls found.");
            return;
        }

        System.out.println("\n  Variables extracted from render_template() calls:\n");
        for (var entry : templateContextVars.entrySet()) {
            String tmpl = entry.getKey();
            Map<String, String> sources = templateContextSources.getOrDefault(tmpl, Map.of());
            List<String> parts = new ArrayList<>();
            for (String var : entry.getValue()) {
                String src = sources.get(var);
                parts.add(src != null ? var + "=" + src : var);
            }
            System.out.printf("  %-35s → %s%n",
                    tmpl,
                    parts.isEmpty() ? "(no variables)" : String.join(", ", parts));
        }
    }
}
