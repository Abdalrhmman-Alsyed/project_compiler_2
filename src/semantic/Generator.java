package semantic;

import ast.python.expressions.CallNode;
import ast.python.expressions.ExpressionNode;
import ast.python.expressions.IdentifierNode;
import ast.python.expressions.KeywordArgumentNode;
import ast.python.literals.StringLiteralNode;
import ast.python.visitors.PythonBaseASTVisitor;

import java.util.*;

/**
 * Generator: walks the Python AST and extracts the variables that each
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
public class Generator extends PythonBaseASTVisitor<Void> {

    // template name → set of Jinja context variable names
    private final Map<String, Set<String>> templateContextVars = new LinkedHashMap<>();

    // template name → (Jinja name → Python identifier on the RHS)
    private final Map<String, Map<String, String>> templateContextSources = new LinkedHashMap<>();

    @Override
    public Void visit(CallNode node) {
        if (node.getFunction() instanceof IdentifierNode idNode && "render_template".equals(idNode.getName())) {
            List<ExpressionNode> args = node.getArguments();
            if (!args.isEmpty() && args.get(0) instanceof StringLiteralNode strNode) {
                String templateName = strNode.getValue();
                templateContextVars.computeIfAbsent(templateName, k -> new LinkedHashSet<>());

                for (int i = 1; i < args.size(); i++) {
                    ExpressionNode arg = args.get(i);
                    if (arg instanceof KeywordArgumentNode kwArg) {
                        String varName = kwArg.getName();
                        templateContextVars.get(templateName).add(varName);

                        if (kwArg.getValue() instanceof IdentifierNode rhsId) {
                            templateContextSources
                                    .computeIfAbsent(templateName, k -> new LinkedHashMap<>())
                                    .put(varName, rhsId.getName());
                        }
                    }
                }
            }
        }
        return super.visit(node);
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
        System.out.println("  GENERATOR: Python -> Jinja2 Context Bridge");
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
            System.out.printf("  %-35s -> %s%n",
                    tmpl,
                    parts.isEmpty() ? "(no variables)" : String.join(", ", parts));
        }
    }
}
