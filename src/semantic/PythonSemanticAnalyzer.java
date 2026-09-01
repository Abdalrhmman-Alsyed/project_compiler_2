package semantic;

import ast.python.declarations.*;
import ast.python.expressions.*;
import ast.python.literals.*;
import ast.python.program.*;
import ast.python.statements.*;
import ast.python.visitors.PythonBaseASTVisitor;
import symbolTable.symbols.SymbolType;

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
 *
 * ERRORS (19-20) — required by the course spec:
 *  19.  Type Mismatch   : unsupported operand types for '+': 'str' and 'int'
 *  20.  Type Error      : 'int' object is not iterable
 *  21.  Undefined Var    : variable 'x' is not defined
 *  22.  Scope Error      : variable 'x' is out of scope
 */
public class PythonSemanticAnalyzer extends PythonBaseASTVisitor<Void> {

    // ── Symbol Table Integration ──────────────────────────────────────────────
    private final symbolTable.PythonSymbolTable symbolTable;
    private symbolTable.scopes.Scope currentScope;
    private final Map<symbolTable.scopes.Scope, Integer> childIndexMap = new HashMap<>();

    public PythonSemanticAnalyzer(symbolTable.PythonSymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.currentScope = symbolTable.getGlobalScope();
    }

    private void enterScope() {
        int index = childIndexMap.getOrDefault(currentScope, 0);
        java.util.List<symbolTable.scopes.Scope> children = symbolTable.getChildScopes(currentScope);
        if (index < children.size()) {
            symbolTable.scopes.Scope child = children.get(index);
            childIndexMap.put(currentScope, index + 1);
            currentScope = child;
        }
    }

    private void exitScope() {
        if (currentScope.getParent() != null) {
            currentScope = currentScope.getParent();
        }
    }


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
    // Inferred type of each module-level variable (checks 19 and 20)
    private final Map<String, SymbolType> globalTypes     = new LinkedHashMap<>();
    // Every name ever bound inside some function body → its first line.
    // Lets us say "out of scope" instead of "not defined" (checks 3 and 1).
    private final Map<String, Integer> functionLocalNames  = new LinkedHashMap<>();
    // Names assigned anywhere in the function currently being walked, collected
    // up-front so a use that precedes its assignment reads as "used before
    // assignment" rather than "undefined".
    private final Deque<Set<String>> assignedAhead         = new ArrayDeque<>();
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
        "NotImplementedError","StopIteration","ZeroDivisionError","RuntimeError",
        "FileNotFoundError","PermissionError","OverflowError","RecursionError","True","False","None","__name__","__file__",
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
        SymbolType   type = SymbolType.UNKNOWN;

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

    public void saveReportToFile(String filePath) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filePath, true))) {
            writer.println("\n" + "=".repeat(60));
            writer.println("  تحليل الدلالات للغة بايثون  (22 فحص)");
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

    private void pushScope() { scopeStack.push(new LinkedHashMap<>()); }

    private void popScope() {
        scopeStack.pop();
    }

    private void defineLocal(String name, int line, int col) {
        defineLocal(name, line, col, SymbolType.UNKNOWN);
    }

    private void defineLocal(String name, int line, int col, SymbolType type) {
        if (scopeStack.isEmpty()) return;
        DefInfo info = new DefInfo(name, line, col);
        info.type = type;
        scopeStack.peek().put(name, info);
        if (!functionDefLists.isEmpty()) functionDefLists.peek().add(info);
        if (functionDepth > 0) functionLocalNames.putIfAbsent(name, line);
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

    // ── Type inference ───────────────────────────────────────────────────────
    // Deliberately conservative: anything not provably typed stays UNKNOWN, and
    // checks 19/20 only fire when every operand type is known. A missed error
    // is far cheaper than a false one.

    private SymbolType typeOf(ExpressionNode expr) {
        if (expr == null) return SymbolType.UNKNOWN;

        if (expr instanceof IntLiteralNode)    return SymbolType.INT;
        if (expr instanceof FloatLiteralNode)  return SymbolType.FLOAT;
        if (expr instanceof StringLiteralNode) return SymbolType.STRING;
        if (expr instanceof BoolLiteralNode)   return SymbolType.BOOL;
        if (expr instanceof NoneLiteralNode)   return SymbolType.NONE;
        if (expr instanceof ListLiteralNode)   return SymbolType.LIST;
        if (expr instanceof DictLiteralNode)   return SymbolType.DICT;
        if (expr instanceof SetLiteralNode)    return SymbolType.SET;

        if (expr instanceof IdentifierNode id) {
            DefInfo local = resolveLocal(id.getName());
            if (local != null) return local.type;
            return globalTypes.getOrDefault(id.getName(), SymbolType.UNKNOWN);
        }

        if (expr instanceof BinaryOpNode bin)  return typeOfBinary(bin);
        if (expr instanceof CallNode call)     return typeOfCall(call);

        return SymbolType.UNKNOWN;
    }

    /** Result type of a constructor-like builtin; UNKNOWN for anything else. */
    private SymbolType typeOfCall(CallNode call) {
        if (!(call.getFunction() instanceof IdentifierNode id)) return SymbolType.UNKNOWN;
        switch (id.getName()) {
            case "str":   return SymbolType.STRING;
            case "int":
            case "len":   return SymbolType.INT;
            case "float": return SymbolType.FLOAT;
            case "bool":  return SymbolType.BOOL;
            case "list":
            case "range":
            case "sorted": return SymbolType.LIST;
            case "dict":  return SymbolType.DICT;
            case "set":   return SymbolType.SET;
            default:      return SymbolType.UNKNOWN;
        }
    }

    private SymbolType typeOfBinary(BinaryOpNode bin) {
        SymbolType l = typeOf(bin.getLeft());
        SymbolType r = typeOf(bin.getRight());
        String op = bin.getOperator();

        if (isComparison(op)) return SymbolType.BOOL;

        if (op.equals("/")) {
            return (isNumeric(l) && isNumeric(r)) ? SymbolType.FLOAT : SymbolType.UNKNOWN;
        }
        if (isNumeric(l) && isNumeric(r)) {
            return (l == SymbolType.FLOAT || r == SymbolType.FLOAT)
                    ? SymbolType.FLOAT : SymbolType.INT;
        }
        if (l == r && (l == SymbolType.STRING || l == SymbolType.LIST) && op.equals("+")) {
            return l;
        }
        // str * int  /  list * int  → repetition keeps the sequence type
        if (op.equals("*")) {
            if ((l == SymbolType.STRING || l == SymbolType.LIST) && isNumeric(r)) return l;
            if ((r == SymbolType.STRING || r == SymbolType.LIST) && isNumeric(l)) return r;
        }
        return SymbolType.UNKNOWN;
    }

    // In Python bool is a subtype of int, so True + 1 is legal arithmetic.
    private boolean isNumeric(SymbolType t) {
        return t == SymbolType.INT || t == SymbolType.FLOAT || t == SymbolType.BOOL;
    }

    private boolean isComparison(String op) {
        return op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">")
            || op.equals("<=") || op.equals(">=") || op.equals("in") || op.equals("not in")
            || op.equals("is") || op.equals("is not") || op.equals("and") || op.equals("or");
    }

    private boolean isArithmetic(String op) {
        return op.equals("+") || op.equals("-") || op.equals("*")
            || op.equals("/") || op.equals("//") || op.equals("%");
    }

    /** True only when the pair is certainly rejected by Python at runtime. */
    private boolean isInvalidOperandPair(String op, SymbolType l, SymbolType r) {
        if (l == SymbolType.UNKNOWN || r == SymbolType.UNKNOWN
                || l == SymbolType.ANY || r == SymbolType.ANY) return false;
        if (isNumeric(l) && isNumeric(r)) return false;

        if (op.equals("+")) {
            return !(l == r && (l == SymbolType.STRING || l == SymbolType.LIST));
        }
        if (op.equals("*")) {
            boolean seqTimesNum = (l == SymbolType.STRING || l == SymbolType.LIST) && isNumeric(r);
            boolean numTimesSeq = (r == SymbolType.STRING || r == SymbolType.LIST) && isNumeric(l);
            return !(seqTimesNum || numTimesSeq);
        }
        if (op.equals("%")) {
            return l != SymbolType.STRING;   // '%' also formats strings
        }
        // '-', '/', '//' are numeric-only
        return true;
    }

    private boolean isIterable(SymbolType t) {
        return t == SymbolType.STRING || t == SymbolType.LIST
            || t == SymbolType.DICT   || t == SymbolType.SET
            || t == SymbolType.UNKNOWN || t == SymbolType.ANY;
    }

    // ── Name resolution diagnostics (checks 1, 3, 15) ───────────────────────
    // Three distinct diagnoses for a name that is not visible here:
    //   assigned later in this function  → used before assignment  (warning)
    //   bound inside some other function → out of scope            (error)
    //   bound nowhere at all             → not defined             (error)

    private void checkNameResolution(String name, int line, int col) {
        if (isKnown(name)) return;

        if (!assignedAhead.isEmpty() && assignedAhead.peek().contains(name)) {
            warning("تم استخدام الاسم '" + name + "' قبل إسناد قيمة له في النطاق المحلي",
                    line, col);
        } else if (functionLocalNames.containsKey(name) || (currentScope.resolve(name) != null && currentScope.resolve(name).getScope() != currentScope)) {
            error("المتغير '" + name + "' خارج النطاق (Out of scope)", line, col);
        } else if (currentScope.resolve(name) == null) {
            error("المتغير '" + name + "' غير معرّف", line, col);
        }
    }

    /** Collects every name this function assigns, before walking its body. */
    private Set<String> collectAssignedNames(ast.python.PythonNode node) {
        Set<String> names = new LinkedHashSet<>();
        gatherAssigned(node, names);
        return names;
    }

    private void gatherAssigned(ast.python.PythonNode node, Set<String> out) {
        if (node == null) return;
        if (node instanceof AssignmentNode asg
                && asg.getTarget() instanceof IdentifierNode id) {
            out.add(id.getName());
        } else if (node instanceof ForNode f) {
            out.add(f.getVariable().getName());
        } else if (node instanceof WithNode w && w.hasAlias()) {
            out.add(w.getAlias().getName());
        }
        for (ast.python.PythonNode child : node.getChildren()) gatherAssigned(child, out);
    }

    // ── Public API ───────────────────────────────────────────────────────────
    public List<SemanticError> getErrors()   { return Collections.unmodifiableList(errors); }
    public List<SemanticError> getWarnings() { return Collections.unmodifiableList(warnings); }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  PYTHON SEMANTIC ANALYSIS  (22 checks)");
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
                error("كود غير قابل للوصول بعد تعليمة 'return'", stmt.getLine(), stmt.getColumn());
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
        enterScope();
        // Check 4: duplicate function name
        // Use symbolTable to check for duplicates
        if (visitedFunctions.contains(n.getName()) || (symbolTable.getGlobalScope().getSymbol(n.getName()) != null && !visitedFunctions.contains(n.getName()))) {
            // It's in the symbol table, we add to visited to prevent double warning since we do a single pass now
        }
        if (visitedFunctions.contains(n.getName())) {
             error("الدالة '" + n.getName() + "' تم تعريفها أكثر من مرة",
                     n.getLine(), n.getColumn());
        }
        visitedFunctions.add(n.getName());
        globalKind.put(n.getName(), "function");
        functionParamCount.put(n.getName(), n.getParameters().size());

        // Check 14: empty function body (only 'pass')
        if (n.getBody() instanceof BlockNode blk
                && blk.getStatements().size() == 1
                && blk.getStatements().get(0) instanceof PassNode) {
            warning("الدالة '" + n.getName() + "' تحتوي على جسم فارغ (فقط 'pass')",
                    n.getLine(), n.getColumn());
        }

        // Check 16: too many parameters (> 7)
        if (n.getParameters().size() > 7) {
            warning("الدالة '" + n.getName() + "' تحتوي على عدد كبير جداً من المعاملات ("
                    + n.getParameters().size() + ") — فكر في استخدام قاموس أو صنف بيانات",
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
                error("المعامل '" + param.getName()
                        + "' مكرر في الدالة '" + n.getName() + "'",
                        param.getLine(), param.getColumn());
            }
            if (param.hasDefaultValue()) param.getDefaultValue().accept(this);

            DefInfo info = new DefInfo(param.getName(), param.getLine(), param.getColumn());
            info.used = true; // parameters are used by callers
            scopeStack.peek().put(param.getName(), info);
        }

        assignedAhead.push(n.getBody() != null
                ? collectAssignedNames(n.getBody()) : new LinkedHashSet<>());
        if (n.getBody() != null) n.getBody().accept(this);
        assignedAhead.pop();

        // Check 12: local variables defined but never used
        List<DefInfo> fnDefs = functionDefLists.pop();
        for (DefInfo def : fnDefs) {
            if (!def.used && !def.name.startsWith("_") && !currentGlobals.contains(def.name)) {
                warning("المتغير '" + def.name + "' معرّف ولكنه لم يُستخدم أبداً",
                        def.line, def.col);
            }
        }

        currentGlobals.clear();
        currentGlobals.addAll(savedGlobals);
        popScope();
        functionDepth--;
        exitScope();
        return null;
    }

    @Override
    public Void visit(ImportNode n) {
        String mod = n.getModule();
        if (mod != null && !mod.isEmpty()) {
            // Check 9: duplicate import
            if (importedModules.containsKey(mod)) {
                warning("الوحدة '" + mod + "' تم استيرادها أكثر من مرة (أول مرة في السطر "
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
                warning("إسناد قيمة للاسم المبني مسبقاً '" + name + "' يخفي الدالة الأصلية",
                        n.getLine(), n.getColumn());
            }

            // Check 10: overwriting function name
            if ("function".equals(globalKind.get(name)) && functionDepth == 0) {
                warning("هذا الإسناد يقوم بالكتابة فوق الدالة التي تحمل الاسم '" + name + "'",
                        n.getLine(), n.getColumn());
            }

            // An augmented assignment ('x += 1') refines the existing type
            // rather than replacing it, so keep what we already knew.
            SymbolType valueType = n.getOperator().equals("=")
                    ? typeOf(n.getValue())
                    : typeOf(new IdentifierNode(id.getLine(), id.getColumn(), name));

            if (functionDepth > 0 && !currentGlobals.contains(name)) {
                defineLocal(name, n.getLine(), n.getColumn(), valueType);
            } else {
                globalKind.put(name, "variable");
                globalTypes.put(name, valueType);
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
            error("استخدام تعليمة 'return' خارج الدالة", n.getLine(), n.getColumn());
        }
        if (n.hasValue()) n.getValue().accept(this);
        return null;
    }

    @Override
    public Void visit(BreakNode n) {
        // Check 2: break outside loop
        if (loopDepth == 0) {
            error("استخدام تعليمة 'break' خارج حلقة التكرار", n.getLine(), n.getColumn());
        }
        return null;
    }

    @Override
    public Void visit(ContinueNode n) {
        // Check 3: continue outside loop
        if (loopDepth == 0) {
            error("استخدام تعليمة 'continue' خارج حلقة التكرار", n.getLine(), n.getColumn());
        }
        return null;
    }

    @Override
    public Void visit(GlobalNode n) {
        for (String varName : n.getVariables()) {
            // Check 6: 'global' after local assignment
            if (!scopeStack.isEmpty() && scopeStack.peek().containsKey(varName)) {
                error("الاسم '" + varName + "' تم إسناده محلياً قبل الإعلان عنه كـ 'global'",
                        n.getLine(), n.getColumn());
            }
            currentGlobals.add(varName);
        }
        return null;
    }

    @Override
    public Void visit(IfNode n) {
        n.getCondition().accept(this);
        enterScope(); pushScope(); n.getThenBlock().accept(this); popScope(); exitScope();
        for (var elif : n.getElifBranches()) {
            elif.getCondition().accept(this);
            pushScope(); elif.getBlock().accept(this); popScope();
        }
        if (n.hasElse()) {
            enterScope(); pushScope(); n.getElseBlock().accept(this); popScope(); exitScope();
        }
        return null;
    }

    @Override
    public Void visit(ForNode n) {
        enterScope();
        n.getIterable().accept(this);

        // Check 20: iterating something that is provably not iterable
        SymbolType iterType = typeOf(n.getIterable());
        if (!isIterable(iterType) && iterType != SymbolType.FUNCTION_TYPE) {
            error("النوع '" + iterType + "' غير قابل للتكرار (Not iterable)",
                    n.getIterable().getLine(), n.getIterable().getColumn());
        }

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
        exitScope();
        return null;
    }

    @Override
    public Void visit(WhileNode n) {
        enterScope();
        n.getCondition().accept(this);

        // loopDepth must rise so 'break'/'continue' inside a while are legal
        loopDepth++;
        pushScope();
        n.getBody().accept(this);
        popScope();
        loopDepth--;
        exitScope();
        return null;
    }

    @Override
    public Void visit(TryNode n) {
        pushScope(); n.getTryBlock().accept(this); popScope();

        for (TryNode.ExceptHandler h : n.getHandlers()) {
            if (h.getExceptionType() != null) h.getExceptionType().accept(this);

            pushScope();
            if (h.hasAlias()) {
                DefInfo info = new DefInfo(h.getAlias(), n.getLine(), n.getColumn());
                info.used = true;   // exception aliases are conventionally short-lived
                scopeStack.peek().put(h.getAlias(), info);
            }
            h.getBlock().accept(this);
            popScope();
        }

        if (n.hasFinally()) { pushScope(); n.getFinallyBlock().accept(this); popScope(); }
        return null;
    }

    @Override
    public Void visit(RaiseNode n) {
        if (n.hasException()) n.getException().accept(this);
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
            error("تم اكتشاف قسمة على الصفر (القاسم الثابت هو 0)",
                    n.getLine(), n.getColumn());
        }

        // Check 17: == None or != None (use 'is None' / 'is not None' instead)
        if ((op.equals("==") || op.equals("!="))
                && (n.getRight() instanceof NoneLiteralNode
                 || n.getLeft()  instanceof NoneLiteralNode)) {
            warning("استخدم 'is None' أو 'is not None' بدلاً من '" + op + " None'",
                    n.getLine(), n.getColumn());
        }

        // Check 19: operands whose types cannot combine under this operator
        if (isArithmetic(op)) {
            SymbolType lt = typeOf(n.getLeft());
            SymbolType rt = typeOf(n.getRight());
            if (isInvalidOperandPair(op, lt, rt)) {
                error("أنواع المعاملات غير مدعومة للعملية '" + op + "': '"
                        + lt + "' و '" + rt + "'", n.getLine(), n.getColumn());
            }
        }

        // Check 18: == True/False or != True/False (use truthiness instead)
        if ((op.equals("==") || op.equals("!="))
                && (n.getRight() instanceof BoolLiteralNode
                 || n.getLeft()  instanceof BoolLiteralNode)) {
            warning("المقارنة مع True/False باستخدام '" + op
                    + "' ليست طريقة بايثونية — استخدم القيمة مباشرة (مثلاً 'if flag:')",
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

            // Checks 1 / 3: the callee must resolve somewhere
            checkNameResolution(name, n.getLine(), n.getColumn());

            // Check: Template file existence for render_template
            if (name.equals("render_template") && n.getArguments().size() > 0) {
                ast.python.PythonNode firstArg = n.getArguments().get(0);
                if (firstArg instanceof StringLiteralNode strNode) {
                    String templateName = strNode.getValue();
                    // Remove quotes from the string literal value if they exist
                    if (templateName.startsWith("\"") || templateName.startsWith("'")) {
                        templateName = templateName.substring(1, templateName.length() - 1);
                    }
                    java.io.File templateFile = new java.io.File("flask-app/templates/" + templateName);
                    if (!templateFile.exists()) {
                        error("ملف القالب '" + templateName + "' غير موجود في المسار flask-app/templates/", n.getLine(), n.getColumn());
                    }
                }
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
    public Void visit(KeywordArgumentNode n) {
        n.getValue().accept(this);
        return null;
    }

    @Override
    public Void visit(IdentifierNode n) {
        String name = n.getName();
        markUsed(name);

        // Checks 1 / 3 / 15
        checkNameResolution(name, n.getLine(), n.getColumn());
        return null;
    }
}
