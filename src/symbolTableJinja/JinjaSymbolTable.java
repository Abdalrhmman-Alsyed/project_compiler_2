package symbolTableJinja;

import java.util.*;

public class JinjaSymbolTable {
    // النطاقات المتداخلة
    private final Stack<SymbolScope> scopeStack;
    // exitScope() pops, so the stack alone cannot describe the finished tree.
    // Every scope ever created is retained here for reporting and analysis.
    private final List<SymbolScope> allScopes = new ArrayList<>();
    private SymbolScope currentScope;

    // التتبع
    private final Map<String, List<Integer>> symbolUsages;
    private final List<String> warnings;
    private final List<String> errors;

    // المعلومات الأساسية
    private String templateName;
    private List<String> extendsTemplates;
    private List<String> includedTemplates;

    public JinjaSymbolTable(String templateName) {
        this.templateName = templateName;
        this.scopeStack = new Stack<>();
        this.symbolUsages = new HashMap<>();
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.extendsTemplates = new ArrayList<>();
        this.includedTemplates = new ArrayList<>();

        // إنشاء النطاق العالمي
        currentScope = new SymbolScope("global", null);
        scopeStack.push(currentScope);
        allScopes.add(currentScope);

        // إضافة الرموز المدمجة
        addBuiltInSymbols();
    }

    private void addBuiltInSymbols() {
        // الدوال المدمجة في Jinja2
        defineSymbol(new FunctionSymbol("range", -1, -1));
        defineSymbol(new FunctionSymbol("dict", -1, -1));
        defineSymbol(new FunctionSymbol("list", -1, -1));
        defineSymbol(new FunctionSymbol("cycler", -1, -1));
        defineSymbol(new FunctionSymbol("namespace", -1, -1));
        defineSymbol(new FunctionSymbol("lipsum", -1, -1));
        defineSymbol(new FunctionSymbol("joiner", -1, -1));

        // الدوال المدمجة في Flask
        defineSymbol(new FunctionSymbol("url_for", -1, -1));
        defineSymbol(new FunctionSymbol("get_flashed_messages", -1, -1));

        // المتغيرات العالمية في Flask
        defineSymbol(new VariableSymbol("request", -1, -1, "Request"));
        defineSymbol(new VariableSymbol("session", -1, -1, "Session"));
        defineSymbol(new VariableSymbol("g", -1, -1, "AppContext"));
        defineSymbol(new VariableSymbol("config", -1, -1, "Config"));

        // الفلاتر المدمجة
        defineSymbol(new FilterSymbol("safe", -1, -1));
        defineSymbol(new FilterSymbol("capitalize", -1, -1));
        defineSymbol(new FilterSymbol("lower", -1, -1));
        defineSymbol(new FilterSymbol("upper", -1, -1));
        defineSymbol(new FilterSymbol("title", -1, -1));
        defineSymbol(new FilterSymbol("trim", -1, -1));
        defineSymbol(new FilterSymbol("striptags", -1, -1));
        defineSymbol(new FilterSymbol("wordcount", -1, -1));

        // Keep the table's built-ins aligned with the semantic analyser so a
        // valid filter such as |map or |tojson is never reported as undefined.
        for (String name : List.of(
                "abs", "attr", "batch", "center", "count", "d", "default", "dictsort",
                "e", "escape", "filesizeformat", "first", "float", "forceescape", "format", "groupby",
                "indent", "int", "items", "join", "last", "length", "list", "map", "max", "min",
                "pprint", "random", "reject", "rejectattr", "replace", "reverse", "round",
                "select", "selectattr", "slice", "sort", "string", "sum", "tojson", "truncate",
                "unique", "urlencode", "urlize", "wordwrap", "xmlattr", "nl2br", "b64encode", "b64decode")) {
            defineSymbol(new FilterSymbol(name, -1, -1));
        }
    }

    // === إدارة النطاقات ===
    public void enterScope(String scopeName, JinjaSymbolType scopeType) {
        SymbolScope newScope = new SymbolScope(scopeName, currentScope);
        newScope.setScopeType(scopeType);
        currentScope = newScope;
        scopeStack.push(currentScope);
        allScopes.add(newScope);
    }

    public void exitScope() {
        if (scopeStack.size() > 1) {
            scopeStack.pop();
            currentScope = scopeStack.peek();
        }
    }

    // === إدارة الرموز ===
    public void defineSymbol(JinjaSymbol symbol) {
        currentScope.define(symbol);

        // تتبع التعريف
        symbol.addMetadata("defined_in_scope", currentScope.getName());
        symbol.addMetadata("defined_at_line", symbol.getLine());
    }

    /** Resolves from the current scope outwards through its parent chain. */
    public JinjaSymbol resolveSymbol(String name) {
        return currentScope == null ? null : currentScope.resolve(name);
    }

    /** Resolves anywhere in the template — used by post-traversal analysis,
     *  when every scope has already been exited. */
    public JinjaSymbol resolveAnywhere(String name) {
        for (SymbolScope scope : allScopes) {
            JinjaSymbol symbol = scope.resolveLocal(name);
            if (symbol != null) return symbol;
        }
        return null;
    }

    public void recordSymbolUsage(String name, int line) {
        symbolUsages.computeIfAbsent(name, k -> new ArrayList<>()).add(line);
    }

    // === إدارة معلومات القالب ===
    public void addExtendsTemplate(String templateName) {
        extendsTemplates.add(templateName);
    }

    public void addIncludedTemplate(String templateName) {
        includedTemplates.add(templateName);
    }

    public boolean extendsTemplate() {
        return !extendsTemplates.isEmpty();
    }

    // === التحليل ===
    public void analyze() {
        checkUndefinedVariables();
        checkUnusedBlocks();
        checkUnusedVariables();
        checkMissingBlocks();
    }

    private void checkUndefinedVariables() {
        // التحقق من المتغيرات المستخدمة ولكن غير المعرفة
        for (Map.Entry<String, List<Integer>> entry : symbolUsages.entrySet()) {
            String varName = entry.getKey();
            JinjaSymbol symbol = resolveAnywhere(varName);

            if (symbol == null && !isBuiltIn(varName)) {
                errors.add(String.format("Undefined variable '%s' used at lines: %s",
                        varName, entry.getValue()));
            }
        }
    }

    private void checkUnusedBlocks() {
        // التحقق من البلوكات المعرفة ولكن غير المستخدمة
        for (JinjaSymbol symbol : getAllSymbols()) {
            if (symbol.getType() == JinjaSymbolType.BLOCK) {
                String blockName = symbol.getName();
                if (!symbolUsages.containsKey(blockName) &&
                        !extendsTemplate()) {
                    warnings.add(String.format(
                            "Block '%s' defined but never used (line %d)",
                            blockName, symbol.getLine()));
                }
            }
        }
    }

    private void checkUnusedVariables() {
        // التحقق من المتغيرات المعرفة ولكن غير المستخدمة
        for (JinjaSymbol symbol : getAllSymbols()) {
            if (symbol.getType() == JinjaSymbolType.VARIABLE &&
                    !((VariableSymbol) symbol).isLoopVar()) {

                String varName = symbol.getName();
                if (!symbolUsages.containsKey(varName)) {
                    warnings.add(String.format(
                            "Variable '%s' defined but never used (line %d)",
                            varName, symbol.getLine()));
                }
            }
        }
    }

    private void checkMissingBlocks() {
        // إذا كان القالب يمتد من قالب آخر،
        // تحقق من أن جميع البلوكات المطلوبة موجودة
        // (يمكن تنفيذ هذا إذا كان لديك معلومات عن القالب الأب)
    }

    private boolean isBuiltIn(String name) {
        // قائمة بالرموز المدمجة
        Set<String> builtIns = Set.of(
                "request", "session", "g", "config",
                "url_for", "get_flashed_messages"
        );
        return builtIns.contains(name);
    }

    private List<JinjaSymbol> getAllSymbols() {
        List<JinjaSymbol> allSymbols = new ArrayList<>();
        for (SymbolScope scope : allScopes) {
            allSymbols.addAll(scope.getSymbols().values());
        }
        return allSymbols;
    }

    // === الطباعة والتقرير ===
    public void printSymbolTable() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📋 JINJA2 SYMBOL TABLE: " + templateName);
        System.out.println("=".repeat(60));

        printScopeTree(allScopes.get(0), 0);
    }

    /** Walks the retained scope list as a tree, matching PythonSymbolTable's shape. */
    private void printScopeTree(SymbolScope scope, int depth) {
        printScope(scope, depth);
        for (SymbolScope child : allScopes) {
            if (child.getParent() == scope) {
                printScopeTree(child, depth + 1);
            }
        }
    }

    private void printScope(SymbolScope scope, int depth) {
        String indent = "  ".repeat(depth);
        System.out.printf("%s┌─ Scope: %s%n", indent, scope.getName());

        if (scope.getSymbols().isEmpty()) {
            System.out.printf("%s│  (no symbols)%n", indent);
        } else {
            for (JinjaSymbol symbol : scope.getSymbols().values()) {
                System.out.printf("%s│  • %s%n", indent, symbol);

                // معلومات إضافية حسب النوع
                if (symbol instanceof VariableSymbol var) {
                    if (var.isLoopVar()) {
                        System.out.printf("%s│    ↳ Loop variable%n", indent);
                    }
                    if (var.getDefaultValue() != null) {
                        System.out.printf("%s│    ↳ Default: %s%n",
                                indent, var.getDefaultValue());
                    }
                } else if (symbol instanceof BlockSymbol block) {
                    if (block.isOverridden()) {
                        System.out.printf("%s│    ↳ Overridden in child template%n", indent);
                    }
                }
            }
        }

        System.out.printf("%s└─%n", indent);
    }

    public void printAnalysisReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 JINJA2 TEMPLATE ANALYSIS");
        System.out.println("=".repeat(60));

        System.out.println("\n📄 Template: " + templateName);

        if (!extendsTemplates.isEmpty()) {
            System.out.println("↗️  Extends: " + String.join(", ", extendsTemplates));
        }

        if (!includedTemplates.isEmpty()) {
            System.out.println("🔗 Includes: " + String.join(", ", includedTemplates));
        }

        // الإحصائيات
        Map<JinjaSymbolType, Integer> stats = new HashMap<>();
        for (JinjaSymbol symbol : getAllSymbols()) {
            stats.put(symbol.getType(), stats.getOrDefault(symbol.getType(), 0) + 1);
        }

        System.out.println("\n📈 Statistics:");
        stats.forEach((type, count) -> {
            System.out.printf("  %-12s: %d%n", type, count);
        });

        System.out.printf("  %-12s: %d%n", "Total", getAllSymbols().size());

        // الأخطاء والتحذيرات
        if (!errors.isEmpty()) {
            System.out.println("\n❌ Errors:");
            errors.forEach(error -> System.out.println("  • " + error));
        }

        if (!warnings.isEmpty()) {
            System.out.println("\n⚠️  Warnings:");
            warnings.forEach(warning -> System.out.println("  • " + warning));
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            System.out.println("\n✅ No issues found!");
        }
    }

    // === فئة النطاق الداخلية ===
    private static class SymbolScope {
        private final String name;
        private final SymbolScope parent;
        private final Map<String, JinjaSymbol> symbols;
        private JinjaSymbolType scopeType;

        public SymbolScope(String name, SymbolScope parent) {
            this.name = name;
            this.parent = parent;
            this.symbols = new HashMap<>();
        }

        public void setScopeType(JinjaSymbolType type) {
            this.scopeType = type;
        }

        public String getName() {
            return name;
        }

        public SymbolScope getParent() {
            return parent;
        }

        public void define(JinjaSymbol symbol) {
            symbols.put(symbol.getName(), symbol);
        }

        public JinjaSymbol resolveLocal(String name) {
            return symbols.get(name);
        }

        public JinjaSymbol resolve(String name) {
            JinjaSymbol symbol = resolveLocal(name);
            if (symbol != null) return symbol;

            if (parent != null) {
                return parent.resolve(name);
            }

            return null;
        }

        public Map<String, JinjaSymbol> getSymbols() {
            return Collections.unmodifiableMap(symbols);
        }
    }
}
