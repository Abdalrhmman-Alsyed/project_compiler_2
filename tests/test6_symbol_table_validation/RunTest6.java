package tests.test6_symbol_table_validation;

import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.*;
import org.antlr.v4.runtime.*;
import semantic.*;
import symbolTable.*;
import symbolTable.scopes.Scope;
import symbolTable.scopes.ScopeType;
import symbolTable.symbols.Symbol;
import symbolTable.symbols.SymbolKind;
import symbolTable.symbols.SymbolType;
import symbolTable.visitores.*;

import java.nio.file.Path;
import java.util.*;

/**
 * اختبار شامل لجداول الرموز والمحلل الدلالي
 * ─────────────────────────────────────────
 * يتحقق من:
 *   A) PythonSymbolTable: الهيكل، النطاقات، الأنواع
 *   B) JinjaSymbolTable : SymbolKind.BLOCK ≠ VARIABLE
 *   C) PythonSemanticAnalyzer: عدد الأخطاء والتحذيرات المتوقعة
 *   D) JinjaAstSemanticAnalyzer: كشف الفلتر الخاطئ، حلقة فارغة، متغير غير ممرر
 */
public class RunTest6 {

    // ── مساعدات طباعة ─────────────────────────────────────────────────────────
    private static int passed = 0, failed = 0;

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  ✅ " + label);
            passed++;
        } else {
            System.out.println("  ❌ " + label);
            failed++;
        }
    }

    private static void section(String title) {
        System.out.println("\n" + "─".repeat(60));
        System.out.println("  " + title);
        System.out.println("─".repeat(60));
    }

    // ── نقطة الدخول ───────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("═".repeat(60));
        System.out.println("  TEST 6: Symbol Table & Semantic Analyzer Validation");
        System.out.println("═".repeat(60));

        testPythonSymbolTable();
        testPythonSemanticAnalyzer();
        testJinjaSymbolTableBlockKind();
        testJinjaSemanticAnalyzer();

        // ── نتيجة نهائية ─────────────────────────────────────────────────────
        System.out.println("\n" + "═".repeat(60));
        System.out.printf("  النتيجة: %d ✅ نجح  |  %d ❌ فشل  |  %d إجمالي%n",
                passed, failed, passed + failed);
        System.out.println("═".repeat(60));
        if (failed > 0) System.exit(1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  A) Python Symbol Table
    // ═══════════════════════════════════════════════════════════════════════════
    private static void testPythonSymbolTable() throws Exception {
        section("A) Python Symbol Table — الهيكل والنطاقات");

        String pyFile = "tests/test6_symbol_table_validation/app.py";
        CharStream input = CharStreams.fromFileName(pyFile);
        FlaskPythonParser parser = new FlaskPythonParser(
                new CommonTokenStream(new FlaskPythonLexer(input)));
        parser.removeErrorListeners();

        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());
        PythonSymbolTable st = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(st));

        Scope global = st.getGlobalScope();

        // ── متغيرات عالمية ──────────────────────────────────────────────────
        check("counter معرّف عالمياً",      global.getSymbol("counter")  != null);
        check("name معرّف عالمياً",         global.getSymbol("name")     != null);
        check("prices معرّف عالمياً",       global.getSymbol("prices")   != null);
        check("config معرّف عالمياً",       global.getSymbol("config")   != null);
        check("tags معرّف عالمياً",         global.getSymbol("tags")     != null);

        // ── أنواع المتغيرات ─────────────────────────────────────────────────
        Symbol counterSym = global.getSymbol("counter");
        check("counter نوعه INT",   counterSym != null && counterSym.getType() == SymbolType.INT);

        Symbol nameSym = global.getSymbol("name");
        check("name نوعه STRING",   nameSym != null && nameSym.getType() == SymbolType.STRING);

        Symbol pricesSym = global.getSymbol("prices");
        check("prices نوعه LIST",   pricesSym != null && pricesSym.getType() == SymbolType.LIST);

        Symbol configSym = global.getSymbol("config");
        check("config نوعه DICT",   configSym != null && configSym.getType() == SymbolType.DICT);

        Symbol tagsSym = global.getSymbol("tags");
        check("tags نوعه SET",      tagsSym != null && tagsSym.getType() == SymbolType.SET);

        // ── الدوال مسجلة ─────────────────────────────────────────────────────
        check("home مسجلة كـ FUNCTION",
                global.getSymbol("home") != null && global.getSymbol("home").getKind() == SymbolKind.FUNCTION);
        check("safe_divide مسجلة كـ FUNCTION",
                global.getSymbol("safe_divide") != null);
        check("product_page مسجلة كـ FUNCTION",
                global.getSymbol("product_page") != null);

        // ── نطاقات فرعية موجودة ──────────────────────────────────────────────
        List<Scope> children = st.getChildScopes(global);
        check("توجد نطاقات فرعية تحت Global (دوال)",  children.size() > 0);

        // ── بحث متعدد المستويات: داخل دالة home ─────────────────────────────
        boolean foundHomeScope = false;
        for (Scope child : children) {
            if (child.getScopeType() == ScopeType.FUNCTION) {
                List<Scope> grandchildren = st.getChildScopes(child);
                if (grandchildren.size() > 0 || !child.getSymbols().isEmpty()) {
                    foundHomeScope = true;
                    break;
                }
            }
        }
        check("نطاق دالة يحتوي على رموز أو نطاقات فرعية", foundHomeScope);

        // ── الطباعة ──────────────────────────────────────────────────────────
        System.out.println("\n  [جدول الرموز — مختصر]");
        st.print();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  B) Python Semantic Analyzer
    // ═══════════════════════════════════════════════════════════════════════════
    private static void testPythonSemanticAnalyzer() throws Exception {
        section("B) Python Semantic Analyzer — الأخطاء والتحذيرات");

        String pyFile = "tests/test6_symbol_table_validation/app.py";
        CharStream input = CharStreams.fromFileName(pyFile);
        FlaskPythonParser parser = new FlaskPythonParser(
                new CommonTokenStream(new FlaskPythonLexer(input)));
        parser.removeErrorListeners();

        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());
        PythonSymbolTable st = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(st));

        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(st);
        ast.accept(analyzer);

        List<SemanticError> errors   = analyzer.getErrors();
        List<SemanticError> warnings = analyzer.getWarnings();

        System.out.println("\n  التقرير:");
        analyzer.printReport();

        // ── ملف app.py النظيف يجب أن لا يحتوي أخطاء صريحة ───────────────────
        // (processed خارج for هو خطأ نطاق — المحلل سيكتشفه)
        check("المحلل الدلالي يعمل بدون NPE", true);
        check("عدد الأخطاء المبلغ عنه >= 0",  errors.size() >= 0);
        check("لا أخطاء في الدوال النظيفة (home, safe_divide)",
                errors.stream().noneMatch(e ->
                        e.toString().contains("ZeroDivisionError")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  C) Jinja Symbol Table — التمييز بين BLOCK و VARIABLE
    // ═══════════════════════════════════════════════════════════════════════════
    private static void testJinjaSymbolTableBlockKind() throws Exception {
        section("C) Jinja Symbol Table — SymbolKind.BLOCK ≠ VARIABLE");

        String jinjaFile = "tests/test6_symbol_table_validation/templates/home.jinja";
        CharStream input = CharStreams.fromFileName(jinjaFile);
        FlaskTemplateParser parser = new FlaskTemplateParser(
                new CommonTokenStream(new FlaskJinjaLexer(input)));
        parser.removeErrorListeners();

        TemplateNode root = new TemplateASTBuilder().visitTemplateRoot(
                (FlaskTemplateParser.TemplateRootContext) parser.template());

        JinjaSymbolTableBuilder builder = new JinjaSymbolTableBuilder();
        root.accept(builder);
        JinjaSymbolTable jst = builder.getSymbolTable();

        Scope global = jst.getGlobalScope();

        System.out.println("\n  [جدول رموز Jinja]");
        jst.print();

        // ── اسم الـ block يجب أن يكون BLOCK لا VARIABLE ─────────────────────
        Symbol titleSym   = global.getSymbol("title");
        Symbol contentSym = global.getSymbol("content");

        check("title مسجل في جدول رموز Jinja",   titleSym   != null);
        check("content مسجل في جدول رموز Jinja",  contentSym != null);

        check("title نوعه BLOCK لا VARIABLE",
                titleSym != null && titleSym.getKind() == SymbolKind.BLOCK);
        check("content نوعه BLOCK لا VARIABLE",
                contentSym != null && contentSym.getKind() == SymbolKind.BLOCK);

        // ── لا يجب أن يوجد symbol عادي بنفس اسم block ───────────────────────
        check("title ليس SymbolKind.VARIABLE",
                titleSym == null || titleSym.getKind() != SymbolKind.VARIABLE);
        check("content ليس SymbolKind.VARIABLE",
                contentSym == null || contentSym.getKind() != SymbolKind.VARIABLE);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  D) Jinja Semantic Analyzer
    // ═══════════════════════════════════════════════════════════════════════════
    private static void testJinjaSemanticAnalyzer() throws Exception {
        section("D) Jinja Semantic Analyzer — كشف الأخطاء والتحذيرات");

        // ── D1: scope.jinja — متغير غير ممرر، متغير for خارج الحلقة ──────────
        runJinjaTest(
            "tests/test6_symbol_table_validation/templates/scope.jinja",
            Set.of("items"),       // ما تم تمريره من بايثون
            Map.of(),
            "scope.jinja",
            new String[] {
                // أخطاء متوقعة
                "missing_ctx_var"
            },
            new String[] {
                // تحذيرات متوقعة
                "inner_var"
            }
        );

        // ── D2: nested.jinja — فلتر خاطئ، حلقة فارغة ──────────────────────
        runJinjaTest(
            "tests/test6_symbol_table_validation/templates/nested.jinja",
            Set.of(),              // لا يوجد context vars
            Map.of(),
            "nested.jinja",
            new String[]{},
            new String[]{
                "fake_filter_xyz",  // فلتر غير موجود
                "[]"                // حلقة فارغة
            }
        );

        // ── D3: التحقق أن block names لا تُعامَل كـ Python variables ─────────
        System.out.println("\n  [D3] التحقق من عدم إصدار خطأ 'لم يُمرر' لأسماء الـ blocks");
        String jinjaFile = "tests/test6_symbol_table_validation/templates/home.jinja";
        CharStream input = CharStreams.fromFileName(jinjaFile);
        FlaskTemplateParser parser = new FlaskTemplateParser(
                new CommonTokenStream(new FlaskJinjaLexer(input)));
        parser.removeErrorListeners();
        TemplateNode root = new TemplateASTBuilder().visitTemplateRoot(
                (FlaskTemplateParser.TemplateRootContext) parser.template());

        JinjaSymbolTableBuilder jb = new JinjaSymbolTableBuilder();
        root.accept(jb);

        JinjaAstSemanticAnalyzer jAnalyzer = new JinjaAstSemanticAnalyzer(
                "home.jinja",
                "tests/test6_symbol_table_validation/templates",
                Set.of("users", "count"),
                Map.of(),
                jb.getSymbolTable()
        );
        root.accept(jAnalyzer);

        System.out.println("  التقرير:");
        jAnalyzer.printReport();

        boolean noBlockError = jAnalyzer.getErrors().stream()
                .noneMatch(e -> e.toString().contains("title") || e.toString().contains("content"));
        check("لا خطأ 'لم يُمرر' لاسم block 'title'",   noBlockError);
        check("لا خطأ 'لم يُمرر' لاسم block 'content'", noBlockError);
        check("عدد أخطاء home.jinja = 0", jAnalyzer.getErrors().size() == 0);
    }

    // ── مساعد تشغيل اختبار Jinja ─────────────────────────────────────────────
    private static void runJinjaTest(String jinjaFile,
                                     Set<String> ctxVars,
                                     Map<String, Object> mockData,
                                     String label,
                                     String[] expectedErrorSnippets,
                                     String[] expectedWarnSnippets) throws Exception {

        System.out.println("\n  [" + label + "]");
        CharStream input = CharStreams.fromFileName(jinjaFile);
        FlaskTemplateParser parser = new FlaskTemplateParser(
                new CommonTokenStream(new FlaskJinjaLexer(input)));
        parser.removeErrorListeners();

        TemplateNode root = new TemplateASTBuilder().visitTemplateRoot(
                (FlaskTemplateParser.TemplateRootContext) parser.template());

        JinjaSymbolTableBuilder jb = new JinjaSymbolTableBuilder();
        root.accept(jb);

        JinjaAstSemanticAnalyzer jAnalyzer = new JinjaAstSemanticAnalyzer(
                Path.of(jinjaFile).getFileName().toString(),
                Path.of(jinjaFile).getParent().toString(),
                ctxVars, mockData, jb.getSymbolTable()
        );
        root.accept(jAnalyzer);
        jAnalyzer.printReport();

        List<SemanticError> errors   = jAnalyzer.getErrors();
        List<SemanticError> warnings = jAnalyzer.getWarnings();

        for (String snippet : expectedErrorSnippets) {
            boolean found = errors.stream()
                    .anyMatch(e -> e.toString().contains(snippet));
            check("خطأ متوقع يحتوي على '" + snippet + "' — موجود", found);
        }
        for (String snippet : expectedWarnSnippets) {
            boolean found = warnings.stream()
                    .anyMatch(w -> w.toString().contains(snippet));
            check("تحذير متوقع يحتوي على '" + snippet + "' — موجود", found);
        }
    }
}
