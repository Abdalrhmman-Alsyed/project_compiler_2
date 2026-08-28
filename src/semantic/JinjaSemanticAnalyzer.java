package semantic;

import gen.FlaskTemplateParser;
import gen.FlaskTemplateParserBaseVisitor;

import java.io.File;
import java.util.*;

/**
 * Performs 10 semantic checks on Jinja2 template parse trees:
 *
 * ERRORS  (1-3):
 *   1. Duplicate block name in the same template
 *   2. {% extends %} used more than once
 *   3. Template extends itself (circular dependency)
 *
 * WARNINGS (4-10):
 *   4.  Unknown Jinja2 filter name
 *   5.  For-loop variable used after the loop has ended
 *   6.  {% include %} references a file that does not exist on disk
 *   7.  {% set %} redefines a variable passed from Python via render_template
 *   8.  {% set %} redefines a variable already set in the same template
 *   9.  {% extends %} is not the first Jinja2 statement in the file
 *  10.  {% for %} loop iterates over a literal empty list []
 */
public class JinjaSemanticAnalyzer extends FlaskTemplateParserBaseVisitor<Void> {

    // ── Config ────────────────────────────────────────────────────────────────
    private final String templateName;   // e.g. "index.html"
    private final String templateDir;    // e.g. "test/jinja"
    private final Set<String> pythonContextVars; // vars from Generator for this template

    // ── Collected issues ──────────────────────────────────────────────────────
    private final List<SemanticError> errors   = new ArrayList<>();
    private final List<SemanticError> warnings = new ArrayList<>();

    // ── Block tracking (check 1) ──────────────────────────────────────────────
    private final Map<String, Integer> seenBlocks = new LinkedHashMap<>(); // name → line

    // ── Extends tracking (checks 2, 3, 9) ────────────────────────────────────
    private int     extendsCount          = 0;
    private boolean sawJinjaBeforeExtends = false; // tracks whether any Jinja2 stmt came first

    // ── Set-variable tracking (check 8) ──────────────────────────────────────
    private final Map<String, Integer> setVarLines = new LinkedHashMap<>(); // name → first-set line

    // ── For-loop variable tracking (check 5) ─────────────────────────────────
    private final Deque<String> activeLoopVars = new ArrayDeque<>();
    // Names bound by {% with x = ... %} while that block is being walked
    private final Deque<String> activeWithVars = new ArrayDeque<>();
    private final Set<String>   expiredLoopVars = new LinkedHashSet<>();

    // ── Always available: Jinja globals + Flask context processors ───────────
    private static final Set<String> BUILTIN_NAMES = Set.of(
        "loop", "range", "dict", "list", "cycler", "namespace", "lipsum", "joiner",
        "url_for", "get_flashed_messages", "request", "session", "g", "config",
        "self", "super", "true", "false", "none", "True", "False", "None"
    );

    // ── Known Jinja2 built-in filters ─────────────────────────────────────────
    private static final Set<String> KNOWN_FILTERS = Set.of(
        "abs","attr","batch","capitalize","center","count","d","default","dictsort",
        "e","escape","filesizeformat","first","float","forceescape","format","groupby",
        "indent","int","items","join","last","length","list","lower","map","max","min",
        "pprint","random","reject","rejectattr","replace","reverse","round","safe",
        "select","selectattr","slice","sort","string","striptags","sum","title","tojson",
        "trim","truncate","unique","upper","urlencode","urlize","wordcount","wordwrap",
        "xmlattr","nl2br","b64encode","b64decode"
    );

    // ── Constructor ───────────────────────────────────────────────────────────
    public JinjaSemanticAnalyzer(String templateName, String templateDir,
                                  Set<String> pythonContextVars) {
        this.templateName     = templateName;
        this.templateDir      = templateDir;
        this.pythonContextVars = pythonContextVars != null ? pythonContextVars : Set.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void error(String msg, int line, int col) {
        errors.add(new SemanticError(SemanticError.Severity.ERROR, msg, line, col));
    }

    private void warning(String msg, int line, int col) {
        warnings.add(new SemanticError(SemanticError.Severity.WARNING, msg, line, col));
    }

    private void markOtherJinjaStatement() {
        // Called for every Jinja2 statement that is NOT extends
        sawJinjaBeforeExtends = true;
    }

    public List<SemanticError> getErrors()   { return Collections.unmodifiableList(errors); }
    public List<SemanticError> getWarnings() { return Collections.unmodifiableList(warnings); }

    // ── Report ────────────────────────────────────────────────────────────────
    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  JINJA2 SEMANTIC ANALYSIS: " + templateName + "  (11 checks)");
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

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 1 + CHECK 9: Block names + extends ordering
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitBlockBlock(FlaskTemplateParser.BlockBlockContext ctx) {
        String blockName = ctx.BLOCK_ID(0).getText();
        int    line      = ctx.start.getLine();
        int    col       = ctx.start.getCharPositionInLine();

        // Check 1: duplicate block name
        if (seenBlocks.containsKey(blockName)) {
            error("Block '" + blockName + "' is defined more than once (first at line "
                    + seenBlocks.get(blockName) + ")", line, col);
        } else {
            seenBlocks.put(blockName, line);
        }

        markOtherJinjaStatement();
        return super.visitBlockBlock(ctx);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECKS 2, 3, 9: extends
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitExtendsBlock(FlaskTemplateParser.ExtendsBlockContext ctx) {
        int    line         = ctx.start.getLine();
        int    col          = ctx.start.getCharPositionInLine();
        String extendsName  = stripQuotes(ctx.BLOCK_STRING().getText());

        // Check 2: extends used more than once
        extendsCount++;
        if (extendsCount > 1) {
            error("{% extends %} used more than once in template '" + templateName + "'",
                    line, col);
        }

        // Check 3: template extends itself
        if (templateName.equals(extendsName)) {
            error("Template '" + templateName + "' extends itself — circular dependency",
                    line, col);
        }

        // Check 9: extends is not the first Jinja2 statement
        if (sawJinjaBeforeExtends) {
            warning("{% extends \"" + extendsName + "\" %} should be the first "
                    + "Jinja2 statement but other Jinja2 content precedes it",
                    line, col);
        }

        return super.visitExtendsBlock(ctx);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 4: Unknown filter
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitFilterExpr(FlaskTemplateParser.FilterExprContext ctx) {
        // expression-mode filter: {{ x | filtername }}
        String filterName = ctx.EXPR_ID().getText();
        checkFilter(filterName, ctx.start.getLine(), ctx.start.getCharPositionInLine());
        return super.visitFilterExpr(ctx);
    }

    @Override
    public Void visitBlockFilterOp(FlaskTemplateParser.BlockFilterOpContext ctx) {
        // block-mode filter: {% x | filtername %}
        String filterName = ctx.BLOCK_ID().getText();
        checkFilter(filterName, ctx.start.getLine(), ctx.start.getCharPositionInLine());
        return super.visitBlockFilterOp(ctx);
    }

    private void checkFilter(String name, int line, int col) {
        if (!KNOWN_FILTERS.contains(name)) {
            warning("Unknown Jinja2 filter '" + name + "' — not in known filter list",
                    line, col);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 5: For-loop variable used after loop ends
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitForBlock(FlaskTemplateParser.ForBlockContext ctx) {
        String loopVar = ctx.BLOCK_ID().getText();
        markOtherJinjaStatement();

        // Check 10: for loop over a literal empty list
        if (ctx.blockExpression() != null
                && ctx.blockExpression().getText().trim().equals("[]")) {
            warning("{% for " + loopVar + " in [] %} iterates over a literal empty list"
                    + " — loop body never executes",
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        // The block now owns its body, so the variable's scope is exactly the
        // span of this rule: push, walk the children, pop.
        activeLoopVars.push(loopVar);
        super.visitForBlock(ctx);
        activeLoopVars.pop();
        expiredLoopVars.add(loopVar);

        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 6: include of non-existent file
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitIncludeBlock(FlaskTemplateParser.IncludeBlockContext ctx) {
        String includedFile = stripQuotes(ctx.BLOCK_STRING().getText());
        int    line         = ctx.start.getLine();
        int    col          = ctx.start.getCharPositionInLine();

        File   target = new File(templateDir, includedFile);
        if (!target.exists()) {
            warning("{% include \"" + includedFile + "\" %} references a file that "
                    + "does not exist: " + target.getPath(), line, col);
        }

        markOtherJinjaStatement();
        return super.visitIncludeBlock(ctx);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 7 + CHECK 8: set variable checks
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visitSetBlock(FlaskTemplateParser.SetBlockContext ctx) {
        String varName = ctx.BLOCK_ID().getText();
        int    line    = ctx.start.getLine();
        int    col     = ctx.start.getCharPositionInLine();

        // Check 7: set redefines Python-passed variable
        if (pythonContextVars.contains(varName)) {
            warning("{% set " + varName + " = ... %} redefines the variable '"
                    + varName + "' that was passed from Python via render_template()",
                    line, col);
        }

        // Check 8: set redefines a variable already set in this template
        if (setVarLines.containsKey(varName)) {
            warning("{% set " + varName + " = ... %} redefines a template variable '"
                    + varName + "' already defined via {% set %} at line "
                    + setVarLines.get(varName),
                    line, col);
        } else {
            setVarLines.put(varName, line);
        }

        markOtherJinjaStatement();
        return super.visitSetBlock(ctx);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK 5 (identifier use)
    // ════════════════════════════════════════════════════════════════════════

    // ── Identifier tracking for check 5 ─────────────────────────────────────

    @Override
    public Void visitIdentifierExpr(FlaskTemplateParser.IdentifierExprContext ctx) {
        // Member access is now a postfix AST/parse-tree node, so this atom is
        // always the root identifier itself (for example "product").
        String name = ctx.EXPR_ID().getText();
        int line = ctx.start.getLine(), col = ctx.start.getCharPositionInLine();
        // order matters: checkExpiredLoopVar clears the name once reported,
        // so ask the context check first or it would see a clean slate
        checkContextVariable(name, line, col);
        checkExpiredLoopVar(name, line, col);
        return super.visitIdentifierExpr(ctx);
    }

    @Override
    public Void visitBlockIdentifier(FlaskTemplateParser.BlockIdentifierContext ctx) {
        String name = ctx.BLOCK_ID().getText();
        int line = ctx.start.getLine(), col = ctx.start.getCharPositionInLine();
        // order matters: checkExpiredLoopVar clears the name once reported,
        // so ask the context check first or it would see a clean slate
        checkContextVariable(name, line, col);
        checkExpiredLoopVar(name, line, col);
        return super.visitBlockIdentifier(ctx);
    }

    /**
     * Check 11 (spec #5): a name used in the template must come from one of
     * six places — render_template kwargs (incl. those of any child template
     * that extends this one), {% set %}, a for-loop variable, a {% with %}
     * binding, or a Jinja/Flask builtin. Anything else was never supplied.
     */
    private void checkContextVariable(String name, int line, int col) {
        if (BUILTIN_NAMES.contains(name))    return;   // builtin
        if (pythonContextVars.contains(name)) return;  // render_template kwarg
        if (setVarLines.containsKey(name))   return;   // {% set %}
        if (activeLoopVars.contains(name))   return;   // {% for x in ... %}
        if (activeWithVars.contains(name))   return;   // {% with x = ... %}
        if (expiredLoopVars.contains(name))  return;   // reported by check 5 instead

        error("template variable '" + name + "' is not passed from render_template",
                line, col);
    }

    private void checkExpiredLoopVar(String name, int line, int col) {
        if (expiredLoopVars.contains(name) && !activeLoopVars.contains(name)) {
            warning("Variable '" + name + "' is a for-loop variable used "
                    + "outside its for-loop scope", line, col);
            expiredLoopVars.remove(name); // report only once per variable
        }
    }

    // ── Generic Jinja block statements that advance the position counter ─────
    @Override
    public Void visitImportBlock(FlaskTemplateParser.ImportBlockContext ctx) {
        markOtherJinjaStatement();
        return super.visitImportBlock(ctx);
    }

    @Override
    public Void visitFromImportBlock(FlaskTemplateParser.FromImportBlockContext ctx) {
        markOtherJinjaStatement();
        return super.visitFromImportBlock(ctx);
    }

    @Override
    public Void visitWithBlock(FlaskTemplateParser.WithBlockContext ctx) {
        markOtherJinjaStatement();

        // {% with messages = get_flashed_messages() %} binds 'messages' for
        // exactly this block — without it every use inside reads as undefined.
        // The grammar deliberately keeps the binding outside blockExpression:
        // {% with messages = get_flashed_messages() %}.  Looking for '=' in
        // the expression therefore always fails and falsely reports messages
        // as an undefined template variable.
        String bound = ctx.BLOCK_ID() == null ? null : ctx.BLOCK_ID().getText();
        if (bound != null && !bound.isEmpty()) activeWithVars.push(bound);

        super.visitWithBlock(ctx);

        if (bound != null && !bound.isEmpty()) activeWithVars.pop();
        return null;
    }

    // ── Utility ──────────────────────────────────────────────────────────────
    private static String stripQuotes(String s) {
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                    || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
