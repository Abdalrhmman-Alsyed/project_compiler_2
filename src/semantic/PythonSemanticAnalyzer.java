package semantic;

import ast.python.declarations.*;
import ast.python.expressions.*;
import ast.python.literals.*;
import ast.python.program.*;
import ast.python.statements.*;
import ast.python.visitors.PythonBaseASTVisitor;

import java.util.*;

/**
 * Performs 15 semantic checks on the Python AST:
 *
 * ERRORS  (1-8):
 *   1. 'return' outside function
 *   2. 'break' outside loop
 *   3. 'continue' outside loop
 *   4. Duplicate function name
 *   5. Duplicate parameter name in same function
 *   6. 'global' declared after local assignment of same name
 *   7. Division by zero (constant literal)
 *   8. Unreachable code after 'return'
 *
 * WARNINGS (9-18):
 *   9.  Duplicate import of same module
 *  10.  Assignment overwrites a function name
 *  11.  Assignment shadows a Python built-in name
 *  12.  Local variable defined but never used
 *  13.  Call to a name that cannot be resolved
 *  14.  Empty function body (only 'pass')
 *  15.  Name used before it is assigned in local scope
 *  16.  Function has too many parameters (> 7)
 *  17.  Comparison with None using == / != instead of is / is not
 *  18.  Comparison with True/False using == / != instead of truthiness
 */
public class PythonSemanticAnalyzer extends PythonBaseASTVisitor<Void> {

    // ── Collected issues ─────────────────────────────────────────────────────
    private final List<SemanticError> errors   = new ArrayList<>();
    private final List<SemanticError> warnings = new ArrayList<>();

    // ── Control-flow depth ───────────────────────────────────────────────────
    private int functionDepth = 0;
    private int loopDepth     = 0;

    // ── Global scope knowledge ────────────────────────────────────────────────
    // Maps name → "function" | "import" | "variable"
    private final Map<String, String>  globalKind        = new LinkedHashMap<>();
    // Maps function name → parameter count (for call-site arity checks)
    private final Map<String, Integer> functionParamCount = new LinkedHashMap<>();
    // Maps module name → first-seen line (for duplicate import check)
    private final Map<String, Integer> importedModules   = new LinkedHashMap<>();
    // Functions that have already been fully visited (for duplicate detection)
    private final Set<String>          visitedFunctions  = new LinkedHashSet<>();

    // ── Local-scope tracking ──────────────────────────────────────────────────
    // Each element of the deque is the local variable map for one scope level.
    private final Deque<Map<String, DefInfo>> scopeStack = new ArrayDeque<>();
    // Variables declared 'global' inside the current function
    private final Set<String> currentGlobals = new HashSet<>();
    // Per-function accumulation of all DefInfo objects (for unused-var check)
    private final Deque<List<DefInfo>> functionDefLists = new ArrayDeque<>();

    // ── Known Python and Flask built-ins ────────────────────────────────────
    private static final Set<String> BUILTINS = Set.of(
        "print","len","range","str","int","float","bool","list","dict","set","tuple",
        "type","isinstance","hasattr","getattr","setattr","callable","enumerate","zip",
        "map","filter","sorted","reversed","min","max","sum","abs","open","input",
        "repr","format","vars","dir","id","hash","hex","oct","bin","super","object",
        "Exception","ValueError","TypeError","KeyError","IndexError","AttributeError",
        "NotImplementedError","StopIteration","True","False","None","__name__","__file__",
        "staticmethod","classmethod","property","any","all","next","iter","bytes",
        "bytearray","complex","frozenset","ord","chr","pow","round","divmod",
        // Flask / common libs always available in a Flask project
        "Flask","render_template","request","redirect","url_for","secure_filename",
        "os","app","jsonify","abort","json"
    );

    // ── Inner model ──────────────────────────────────────────────────────────
    private static class DefInfo {
        final String name;
        final int    line, col;
        boolean      used = false;

        DefInfo(String name, int line, int col) {
            this.name = name; this.line = line; this.col = col;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private void error(String msg, int line, int col) {
        errors.add(new SemanticError(SemanticError.Severity.ERROR, msg, line, col));
    }

    private void warning(String msg, int line, int col) {
        warnings.add(new SemanticError(SemanticError.Severity.WARNING, msg, line, col));
    }

    private void pushScope() { scopeStack.push(new LinkedHashMap<>()); }

    private void popScope() {
        scopeStack.pop();
    }

    private void defineLocal(String name, int line, int col) {
        if (scopeStack.isEmpty()) return;
        DefInfo info = new DefInfo(name, line, col);
        scopeStack.peek().put(name, info);
        if (!functionDefLists.isEmpty()) functionDefLists.peek().add(info);
    }

    private DefInfo resolveLocal(String name) {
        for (Map<String, DefInfo> scope : scopeStack) {
            DefInfo d = scope.get(name);
            if (d != null) return d;
        }
        return null;
    }

    private boolean isKnown(String name) {
        if (resolveLocal(name) != null) return true;
        return globalKind.containsKey(name)
            || importedModules.containsKey(name)
            || currentGlobals.contains(name)
            || BUILTINS.contains(name);
    }

    private void markUsed(String name) {
        DefInfo d = resolveLocal(name);
        if (d != null) d.used = true;
    }

    // ── Public API ───────────────────────────────────────────────────────────
    public List<SemanticError> getErrors()   { return Collections.unmodifiableList(errors); }
    public List<SemanticError> getWarnings() { return Collections.unmodifiableList(warnings); }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  PYTHON SEMANTIC ANALYSIS  (18 checks)");
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
    //  PROGRAM / BLOCK
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visit(ProgramNode n) {
        pushScope();
        // Pre-collect all top-level function and import names so forward references work.
        for (var stmt : n.getStatements()) {
            if (stmt instanceof FunctionNode fn) {
                globalKind.put(fn.getName(), "function");
                functionParamCount.put(fn.getName(), fn.getParameters().size());
            } else if (stmt instanceof ImportNode imp) {
                if (imp.getModule() != null) globalKind.put(imp.getModule(), "import");
                for (var id : imp.getImports()) globalKind.put(id.getName(), "import");
            }
        }
        for (var stmt : n.getStatements()) stmt.accept(this);
        scopeStack.pop(); // global scope — don't report unused
        return null;
    }

    @Override
    public Void visit(BlockNode n) {
        boolean seenReturn = false;
        for (var stmt : n.getStatements()) {
            // Check 8: unreachable code after return
            if (seenReturn && !(stmt instanceof PassNode)) {
                error("Unreachable code after 'return' statement", stmt.getLine(), stmt.getColumn());
                seenReturn = false;
            }
            stmt.accept(this);
            if (stmt instanceof ReturnNode) seenReturn = true;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DECLARATIONS
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visit(FunctionNode n) {
        // Check 4: duplicate function name
        if (visitedFunctions.contains(n.getName())) {
            error("Function '" + n.getName() + "' is defined more than once",
                    n.getLine(), n.getColumn());
        }
        visitedFunctions.add(n.getName());
        globalKind.put(n.getName(), "function");
        functionParamCount.put(n.getName(), n.getParameters().size());

        // Check 14: empty function body (only 'pass')
        if (n.getBody() instanceof BlockNode blk
                && blk.getStatements().size() == 1
                && blk.getStatements().get(0) instanceof PassNode) {
            warning("Function '" + n.getName() + "' has an empty body (only 'pass')",
                    n.getLine(), n.getColumn());
        }

        // Check 16: too many parameters (> 7)
        if (n.getParameters().size() > 7) {
            warning("Function '" + n.getName() + "' has too many parameters ("
                    + n.getParameters().size() + ") — consider using a dict or dataclass",
                    n.getLine(), n.getColumn());
        }

        for (var dec : n.getDecorators()) dec.accept(this);

        functionDepth++;
        pushScope();
        functionDefLists.push(new ArrayList<>());
        Set<String> savedGlobals = new HashSet<>(currentGlobals);
        currentGlobals.clear();

        // Check 5: duplicate parameter name
        Set<String> paramNames = new LinkedHashSet<>();
        for (var param : n.getParameters()) {
            if (!paramNames.add(param.getName())) {
                error("Duplicate parameter '" + param.getName()
                        + "' in function '" + n.getName() + "'",
                        param.getLine(), param.getColumn());
            }
            DefInfo info = new DefInfo(param.getName(), param.getLine(), param.getColumn());
            info.used = true; // parameters are used by callers
            scopeStack.peek().put(param.getName(), info);
        }

        if (n.getBody() != null) n.getBody().accept(this);

        // Check 12: local variables defined but never used
        List<DefInfo> fnDefs = functionDefLists.pop();
        for (DefInfo def : fnDefs) {
            if (!def.used && !def.name.startsWith("_") && !currentGlobals.contains(def.name)) {
                warning("Variable '" + def.name + "' defined but never used",
                        def.line, def.col);
            }
        }

        currentGlobals.clear();
        currentGlobals.addAll(savedGlobals);
        popScope();
        functionDepth--;
        return null;
    }

    @Override
    public Void visit(ImportNode n) {
        String mod = n.getModule();
        if (mod != null && !mod.isEmpty()) {
            // Check 9: duplicate import
            if (importedModules.containsKey(mod)) {
                warning("Module '" + mod + "' imported more than once (first at line "
                        + importedModules.get(mod) + ")",
                        n.getLine(), n.getColumn());
            } else {
                importedModules.put(mod, n.getLine());
                globalKind.put(mod, "import");
            }
        }
        for (var id : n.getImports()) {
            globalKind.put(id.getName(), "import");
            markUsed(id.getName());
        }
        return null;
    }

    @Override
    public Void visit(DecoratorNode n) {
        String root = n.getName().split("\\.")[0];
        markUsed(root);
        for (var arg : n.getArguments()) arg.accept(this);
        return null;
    }

    @Override
    public Void visit(ParameterNode n) { return null; }

    // ════════════════════════════════════════════════════════════════════════
    //  STATEMENTS
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visit(AssignmentNode n) {
        // Visit RHS first (uses identifiers)
        if (n.getValue() != null) n.getValue().accept(this);

        // Process LHS (definition)
        if (n.getTarget() instanceof IdentifierNode id) {
            String name = id.getName();

            // Check 11: shadowing built-in
            if (BUILTINS.contains(name) && !name.equals("__name__") && !name.equals("app")) {
                warning("Assignment to built-in name '" + name + "' shadows the built-in",
                        n.getLine(), n.getColumn());
            }

            // Check 10: overwriting function name
            if ("function".equals(globalKind.get(name)) && functionDepth == 0) {
                warning("Assignment overwrites function name '" + name + "'",
                        n.getLine(), n.getColumn());
            }

            if (functionDepth > 0 && !currentGlobals.contains(name)) {
                defineLocal(name, n.getLine(), n.getColumn());
            } else {
                globalKind.put(name, "variable");
            }
        } else {
            // Complex target (attribute access, index) — just visit for uses
            n.getTarget().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ReturnNode n) {
        // Check 1: return outside function
        if (functionDepth == 0) {
            error("'return' statement outside function", n.getLine(), n.getColumn());
        }
        if (n.hasValue()) n.getValue().accept(this);
        return null;
    }

    @Override
    public Void visit(BreakNode n) {
        // Check 2: break outside loop
        if (loopDepth == 0) {
            error("'break' statement outside loop", n.getLine(), n.getColumn());
        }
        return null;
    }

    @Override
    public Void visit(ContinueNode n) {
        // Check 3: continue outside loop
        if (loopDepth == 0) {
            error("'continue' statement outside loop", n.getLine(), n.getColumn());
        }
        return null;
    }

    @Override
    public Void visit(GlobalNode n) {
        for (String varName : n.getVariables()) {
            // Check 6: 'global' after local assignment
            if (!scopeStack.isEmpty() && scopeStack.peek().containsKey(varName)) {
                error("Name '" + varName + "' assigned locally before 'global' declaration",
                        n.getLine(), n.getColumn());
            }
            currentGlobals.add(varName);
        }
        return null;
    }

    @Override
    public Void visit(IfNode n) {
        n.getCondition().accept(this);
        pushScope(); n.getThenBlock().accept(this); popScope();
        for (var elif : n.getElifBranches()) {
            elif.getCondition().accept(this);
            pushScope(); elif.getBlock().accept(this); popScope();
        }
        if (n.hasElse()) {
            pushScope(); n.getElseBlock().accept(this); popScope();
        }
        return null;
    }

    @Override
    public Void visit(ForNode n) {
        n.getIterable().accept(this);
        loopDepth++;
        pushScope();

        // Loop variable is implicitly "used"
        String loopVarName = n.getVariable().getName();
        DefInfo loopDef = new DefInfo(loopVarName, n.getLine(), n.getColumn());
        loopDef.used = true;
        scopeStack.peek().put(loopVarName, loopDef);
        if (!functionDefLists.isEmpty()) functionDefLists.peek().add(loopDef);

        n.getBody().accept(this);
        popScope();
        loopDepth--;
        return null;
    }

    @Override
    public Void visit(WithNode n) {
        n.getExpression().accept(this);
        if (n.hasAlias()) defineLocal(n.getAlias().getName(), n.getLine(), n.getColumn());
        pushScope(); n.getBody().accept(this); popScope();
        return null;
    }

    @Override
    public Void visit(ExpressionStatementNode n) {
        n.getExpression().accept(this);
        return null;
    }

    @Override public Void visit(PassNode n) { return null; }

    // ════════════════════════════════════════════════════════════════════════
    //  EXPRESSIONS
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Void visit(BinaryOpNode n) {
        n.getLeft().accept(this);
        n.getRight().accept(this);

        // Check 7: division by zero
        String op = n.getOperator();
        if ((op.equals("/") || op.equals("//"))
                && n.getRight() instanceof IntLiteralNode rhs && rhs.getValue() == 0) {
            error("Division by zero detected (constant divisor is 0)",
                    n.getLine(), n.getColumn());
        }

        // Check 17: == None or != None (use 'is None' / 'is not None' instead)
        if ((op.equals("==") || op.equals("!="))
                && (n.getRight() instanceof NoneLiteralNode
                 || n.getLeft()  instanceof NoneLiteralNode)) {
            warning("Use 'is None' or 'is not None' instead of '" + op + " None'",
                    n.getLine(), n.getColumn());
        }

        // Check 18: == True/False or != True/False (use truthiness instead)
        if ((op.equals("==") || op.equals("!="))
                && (n.getRight() instanceof BoolLiteralNode
                 || n.getLeft()  instanceof BoolLiteralNode)) {
            warning("Comparing with True/False using '" + op
                    + "' is not Pythonic — use the value directly (e.g. 'if flag:')",
                    n.getLine(), n.getColumn());
        }

        return null;
    }

    @Override
    public Void visit(UnaryOpNode n) {
        n.getOperand().accept(this);
        return null;
    }

    @Override
    public Void visit(CallNode n) {
        if (n.getFunction() instanceof IdentifierNode id) {
            String name = id.getName();
            markUsed(name);

            // Check 13: call to undefined name
            if (!isKnown(name)) {
                warning("Call to undefined name '" + name + "'",
                        n.getLine(), n.getColumn());
            }
        } else {
            n.getFunction().accept(this);
        }
        for (var arg : n.getArguments()) arg.accept(this);
        return null;
    }

    @Override
    public Void visit(AttributeNode n) {
        n.getObject().accept(this);
        return null;
    }

    @Override
    public Void visit(IndexNode n) {
        n.getCollection().accept(this);
        n.getIndex().accept(this);
        return null;
    }

    @Override
    public Void visit(IdentifierNode n) {
        String name = n.getName();
        markUsed(name);

        // Check 15: name used before assignment in local function scope
        if (functionDepth > 0 && resolveLocal(name) == null && !isKnown(name)) {
            warning("Name '" + name + "' used before being assigned in local scope",
                    n.getLine(), n.getColumn());
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LITERALS
    // ════════════════════════════════════════════════════════════════════════

    @Override public Void visit(IntLiteralNode n)    { return null; }
    @Override public Void visit(FloatLiteralNode n)  { return null; }
    @Override public Void visit(StringLiteralNode n) { return null; }
    @Override public Void visit(BoolLiteralNode n)   { return null; }
    @Override public Void visit(NoneLiteralNode n)   { return null; }

    @Override
    public Void visit(ListLiteralNode n) {
        for (var el : n.getElements()) el.accept(this);
        return null;
    }

    @Override
    public Void visit(DictLiteralNode n) {
        for (var e : n.getEntries()) {
            e.getKey().accept(this);
            e.getValue().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(SetLiteralNode n) {
        for (var el : n.getElements()) el.accept(this);
        return null;
    }
}
