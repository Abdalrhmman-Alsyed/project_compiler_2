package tests.test7_all_semantic_checks;

import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.*;
import org.antlr.v4.runtime.*;
import semantic.*;
import symbolTable.*;
import symbolTable.visitores.*;

import java.nio.file.Path;
import java.util.*;

/**
 * ══════════════════════════════════════════════════════════════════════
 *  اختبار شامل لجميع الفحوصات الدلالية (22 بايثون + 13 جينجا)
 * ══════════════════════════════════════════════════════════════════════
 *
 *  القسم الأول  — Python Errors (أخطاء بايثون):
 *    1.  return خارج دالة
 *    2.  break خارج حلقة
 *    3.  continue خارج حلقة
 *    4.  دالة مكررة الاسم
 *    5.  معامل مكرر في نفس الدالة
 *    6.  global بعد إسناد محلي
 *    7.  قسمة على الصفر (ثابتة)
 *    8.  كود غير قابل للوصول بعد return
 *    9.  عدم تطابق الأنواع (str + int)
 *   10.  دوران على كائن غير قابل للتكرار
 *   11.  متغير غير معرّف
 *   12.  متغير خارج النطاق
 *
 *  القسم الثاني — Python Warnings (تحذيرات بايثون):
 *   W1.  استيراد مكرر
 *   W2.  إسناد يكتب فوق اسم دالة
 *   W3.  متغير محلي غير مستخدم
 *   W4.  استدعاء اسم لا يمكن حله
 *   W5.  دالة فارغة (pass فقط)
 *   W6.  استخدام اسم قبل الإسناد
 *   W7.  أكثر من 7 معاملات
 *   W8.  مقارنة مع None باستخدام ==
 *   W9.  مقارنة مع True/False باستخدام ==
 *   W10. إخفاء دالة مبنية مسبقاً (builtin shadowing)
 *
 *  القسم الثالث — Jinja Errors (أخطاء جينجا):
 *   J1.  block مكرر الاسم
 *   J2.  extends أكثر من مرة
 *   J3.  extends/include لملف غير موجود
 *   J5.  متغير لم يُمرَّر من render_template
 *   J6.  خاصية غير موجودة في mock data
 *   J7.  فهرس خارج حدود القائمة
 *   J8.  url_for('static') لملف غير موجود
 *
 *  القسم الرابع — Jinja Warnings (تحذيرات جينجا):
 *   JW1. فلتر غير معروف
 *   JW2. متغير حلقة for بعد انتهاء الحلقة
 *   JW3. set يُعيد تعريف متغير من بايثون
 *   JW4. كود جينجا قبل extends
 *   JW5. حلقة for على قائمة فارغة
 *
 *  القسم الخامس — التحقق أن الكود الصحيح لا ينتج أخطاء وهمية
 */
public class RunTest7 {

    private static int passed = 0, failed = 0;

    // ── طباعة نتيجة فحص ─────────────────────────────────────────────────────
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
        System.out.println("\n" + "═".repeat(62));
        System.out.println("  " + title);
        System.out.println("═".repeat(62));
    }

    private static void subsection(String title) {
        System.out.println("\n─── " + title + " ───");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  نقطة الدخول
    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        System.out.println("═".repeat(62));
        System.out.println("  TEST 7 — جميع الفحوصات الدلالية (22 Python + 13 Jinja)");
        System.out.println("═".repeat(62));

        // تحليل ملف بايثون
        PythonAnalysisResult pyResult = analyzePython(
            "tests/test7_all_semantic_checks/app.py");

        System.out.println("\n[تقرير المحلل الدلالي لبايثون]");
        pyResult.analyzer.printReport();

        // تحليل ملف جينجا (أخطاء)
        JinjaAnalysisResult jinjaErrResult = analyzeJinja(
            "tests/test7_all_semantic_checks/templates/errors.jinja",
            Set.of("items", "mock_list", "mock_dict"),
            buildMockData()
        );

        System.out.println("\n[تقرير المحلل الدلالي لجينجا — errors.jinja]");
        jinjaErrResult.analyzer.printReport();

        // التحقق من الأخطاء والتحذيرات
        checkPythonErrors(pyResult);
        checkPythonWarnings(pyResult);
        checkJinjaErrors(jinjaErrResult);
        checkJinjaWarnings(jinjaErrResult);
        checkValidCodeNoErrors();

        // ── النتيجة النهائية ─────────────────────────────────────────────────
        System.out.println("\n" + "═".repeat(62));
        System.out.printf("  النتيجة: %d ✅ نجح  |  %d ❌ فشل  |  %d إجمالي%n",
                passed, failed, passed + failed);
        System.out.println("═".repeat(62));
        if (failed > 0) System.exit(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  مساعدات التحليل
    // ══════════════════════════════════════════════════════════════════════════

    private record PythonAnalysisResult(
            List<SemanticError> errors,
            List<SemanticError> warnings,
            PythonSemanticAnalyzer analyzer) {}

    private record JinjaAnalysisResult(
            List<SemanticError> errors,
            List<SemanticError> warnings,
            JinjaAstSemanticAnalyzer analyzer) {}

    private static PythonAnalysisResult analyzePython(String filePath) throws Exception {
        CharStream input = CharStreams.fromFileName(filePath);
        FlaskPythonParser parser = new FlaskPythonParser(
                new CommonTokenStream(new FlaskPythonLexer(input)));
        parser.removeErrorListeners();
        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());

        PythonSymbolTable st = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(st));

        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(st);
        ast.accept(analyzer);
        return new PythonAnalysisResult(analyzer.getErrors(), analyzer.getWarnings(), analyzer);
    }

    private static JinjaAnalysisResult analyzeJinja(String filePath,
                                                     Set<String> ctxVars,
                                                     Map<String, Object> mockData) throws Exception {
        CharStream input = CharStreams.fromFileName(filePath);
        FlaskTemplateParser parser = new FlaskTemplateParser(
                new CommonTokenStream(new FlaskJinjaLexer(input)));
        parser.removeErrorListeners();
        TemplateNode root = new TemplateASTBuilder().visitTemplateRoot(
                (FlaskTemplateParser.TemplateRootContext) parser.template());

        JinjaSymbolTableBuilder jb = new JinjaSymbolTableBuilder();
        root.accept(jb);

        JinjaAstSemanticAnalyzer analyzer = new JinjaAstSemanticAnalyzer(
                Path.of(filePath).getFileName().toString(),
                Path.of(filePath).getParent().toString(),
                ctxVars, mockData, jb.getSymbolTable());
        root.accept(analyzer);
        return new JinjaAnalysisResult(analyzer.getErrors(), analyzer.getWarnings(), analyzer);
    }

    /** بيانات وهمية (Mock Data) لاختبار الخصائص والفهارس */
    private static Map<String, Object> buildMockData() {
        Map<String, Object> data = new HashMap<>();

        // mock_dict: يحتوي على key و nested فقط (لا non_existent_attribute)
        Map<String, Object> dict = new HashMap<>();
        dict.put("key", "value");
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner_key", "inner_val");
        dict.put("nested", nested);
        data.put("mock_dict", dict);

        // mock_list: قائمة بعنصرين فقط (الفهرس 99 خارج الحدود)
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> el1 = new HashMap<>(); el1.put("name", "عنصر 1"); list.add(el1);
        Map<String, Object> el2 = new HashMap<>(); el2.put("name", "عنصر 2"); list.add(el2);
        data.put("mock_list", list);

        // items: قائمة نصوص بسيطة
        data.put("items", List.of("أ", "ب", "ج"));

        return data;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  فحوصات أخطاء بايثون
    // ══════════════════════════════════════════════════════════════════════════
    private static void checkPythonErrors(PythonAnalysisResult r) {
        section("القسم الأول — أخطاء بايثون الدلالية");
        List<SemanticError> errors = r.errors();

        subsection("خطأ 1: استخدام return خارج دالة");
        check("يكتشف 'return' خارج دالة",
                containsAny(errors, "return", "الدالة خارج"));

        subsection("خطأ 2: استخدام break خارج حلقة");
        check("يكتشف 'break' خارج حلقة",
                containsAny(errors, "break", "التكرار حلقة"));

        subsection("خطأ 3: استخدام continue خارج حلقة");
        check("يكتشف 'continue' خارج حلقة",
                containsAny(errors, "continue", "التكرار حلقة"));

        subsection("خطأ 4: دالة مكررة الاسم");
        check("يكتشف الدالة المكررة 'duplicate_func'",
                containsAny(errors, "duplicate_func", "مرة من أكثر"));

        subsection("خطأ 5: معامل مكرر في نفس الدالة");
        check("يكتشف المعامل المكرر 'x' في 'bad_params'",
                containsAny(errors, "bad_params", "مكرر"));

        subsection("خطأ 6: global بعد إسناد محلي");
        check("يكتشف 'global counter' بعد إسناد محلي",
                containsAny(errors, "counter", "global"));

        subsection("خطأ 7: قسمة على الصفر");
        check("يكتشف القسمة على الصفر",
                containsAny(errors, "صفر", "الصفر"));

        subsection("خطأ 8: كود غير قابل للوصول بعد return");
        check("يكتشف الكود غير القابل للوصول",
                containsAny(errors, "قابل", "return"));

        subsection("خطأ 9: عدم تطابق الأنواع (str + int)");
        check("يكتشف خطأ النوع 'str' + 'int'",
                containsAny(errors, "str", "int", "المعاملات"));

        subsection("خطأ 10: دوران على كائن غير قابل للتكرار");
        check("يكتشف الدوران على عدد صحيح غير قابل للتكرار",
                containsAny(errors, "قابل", "iterable", "int"));

        subsection("خطأ 11: متغير غير معرّف");
        check("يكتشف المتغير 'totally_unknown_var' غير المعرّف",
                containsAny(errors, "totally_unknown_var", "معرّف"));

        subsection("خطأ 12: متغير خارج النطاق");
        check("يكتشف الوصول لـ 'my_private_var' خارج نطاقه",
                containsAny(errors, "my_private_var", "النطاق"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  فحوصات تحذيرات بايثون
    // ══════════════════════════════════════════════════════════════════════════
    private static void checkPythonWarnings(PythonAnalysisResult r) {
        section("القسم الثاني — تحذيرات بايثون الدلالية");
        List<SemanticError> warnings = r.warnings();

        subsection("تحذير W1: استيراد مكرر");
        check("يكتشف استيراد 'os' المكرر",
                containsAny(warnings, "os", "مرة من أكثر", "استيرادها"));

        subsection("تحذير W2: إسناد يكتب فوق اسم دالة");
        check("يكتشف الكتابة فوق الدالة 'my_overridable_func'",
                containsAny(warnings, "my_overridable_func", "الدالة", "فوق"));

        subsection("تحذير W3: متغير محلي غير مستخدم");
        check("يكتشف 'unused_local' غير المستخدم",
                containsAny(warnings, "unused_local", "يُستخدم"));

        subsection("تحذير W4: استدعاء اسم لا يمكن حله");
        check("يكتشف استدعاء 'call_to_ghost_function' غير المعرّف",
                containsAny(r.errors(), "call_to_ghost_function", "غير معرّف"));

        subsection("تحذير W5: دالة فارغة (pass فقط)");
        check("يكتشف دالة فارغة تحتوي على pass فقط",
                containsAny(warnings, "pass", "فارغ", "جسم"));

        subsection("تحذير W6: استخدام اسم قبل الإسناد");
        check("يكتشف استخدام 'future_var' قبل إسناده",
                containsAny(warnings, "future_var", "إسناد", "قبل"));

        subsection("تحذير W7: أكثر من 7 معاملات");
        check("يكتشف 'too_many_params' بـ 8 معاملات",
                containsAny(warnings, "too_many_params", "معاملات", "كبير"));

        subsection("تحذير W8: مقارنة مع None باستخدام ==");
        check("يكتشف مقارنة '== None' بدلاً من 'is None'",
                containsAny(warnings, "None", "is"));

        subsection("تحذير W9: مقارنة مع True/False باستخدام ==");
        check("يكتشف مقارنة '== True' بدلاً من if flag",
                containsAny(warnings, "True", "False", "بايثونية"));

        subsection("تحذير W10: إخفاء دالة مبنية مسبقاً");
        check("يكتشف إخفاء الدالة المبنية مسبقاً 'len'",
                containsAny(warnings, "len", "يخفي", "مسبقاً"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  فحوصات أخطاء جينجا
    // ══════════════════════════════════════════════════════════════════════════
    private static void checkJinjaErrors(JinjaAnalysisResult r) {
        section("القسم الثالث — أخطاء جينجا الدلالية");
        List<SemanticError> errors = r.errors();

        subsection("خطأ J1: block مكرر الاسم");
        check("يكتشف block 'same_block' المكرر",
                containsAny(errors, "same_block", "مرة من أكثر", "مرتين"));

        subsection("خطأ J2: extends أكثر من مرة");
        check("يكتشف استخدام extends أكثر من مرة",
                containsAny(errors, "extends", "مرة من أكثر"));

        subsection("خطأ J3: extends/include لملف غير موجود");
        check("يكتشف extends لملف غير موجود 'non_existent_base.jinja'",
                containsAny(errors, "non_existent_base.jinja", "غير موجود"));
        check("يكتشف include لملف غير موجود 'ghost_file_xyz.jinja'",
                containsAny(errors, "ghost_file_xyz.jinja", "غير موجود"));

        subsection("خطأ J5: متغير لم يُمرَّر من render_template");
        check("يكتشف 'missing_var_from_python' غير الممرر",
                containsAny(errors, "missing_var_from_python", "render_template"));

        subsection("خطأ J6: خاصية غير موجودة في mock data");
        check("يكتشف الوصول لـ '.non_existent_attribute'",
                containsAny(errors, "non_existent_attribute", "mock"));

        subsection("خطأ J7: فهرس خارج حدود القائمة");
        check("يكتشف الفهرس 99 خارج حدود القائمة",
                containsAny(errors, "99", "حدود", "bounds", "فهرس"));

        subsection("خطأ J8: url_for('static') لملف غير موجود");
        check("يكتشف ملف static غير موجود 'ghost_image_xyz.png'",
                containsAny(errors, "ghost_image_xyz.png", "static", "موجود"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  فحوصات تحذيرات جينجا
    // ══════════════════════════════════════════════════════════════════════════
    private static void checkJinjaWarnings(JinjaAnalysisResult r) {
        section("القسم الرابع — تحذيرات جينجا الدلالية");
        List<SemanticError> warnings = r.warnings();

        subsection("تحذير JW1: فلتر غير معروف");
        check("يكتشف الفلتر المجهول 'totally_unknown_filter_abc'",
                containsAny(warnings, "totally_unknown_filter_abc", "Filter", "مرشح"));

        subsection("تحذير JW2: متغير حلقة for بعد انتهائها");
        check("يكتشف 'product' مستخدماً خارج حلقة for",
                containsAny(warnings, "product", "حلقة", "نطاق"));

        subsection("تحذير JW3: set يُعيد تعريف متغير من بايثون");
        check("يكتشف set يُعيد تعريف 'items' الممرر من بايثون",
                containsAny(warnings, "items", "set", "بايثون", "render_template"));

        subsection("تحذير JW4: كود جينجا قبل extends");
        check("يكتشف set قبل extends",
                containsAny(warnings, "extends", "قبل"));

        subsection("تحذير JW5: حلقة for على قائمة فارغة");
        check("يكتشف حلقة for على [] الفارغة",
                containsAny(warnings, "[]", "فارغة", "قائمة"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  التحقق أن الكود الصحيح لا ينتج أخطاء وهمية
    // ══════════════════════════════════════════════════════════════════════════
    private static void checkValidCodeNoErrors() throws Exception {
        section("القسم الخامس — التحقق أن الكود الصحيح لا ينتج أخطاء وهمية");

        subsection("Python: دالة valid_python_code الصحيحة");
        PythonAnalysisResult r = analyzePython("tests/test7_all_semantic_checks/app.py");
        // الدوال الصحيحة (home, error_page, valid_python_code) لا يجب أن تُسبب:
        // - خطأ "return outside function" لها
        // - خطأ "undefined variable" لها
        check("لا خطأ خاص بالدوال الصحيحة home/error_page/valid_python_code",
                r.errors().stream().noneMatch(e ->
                        e.toString().contains("home") ||
                        e.toString().contains("error_page") ||
                        e.toString().contains("valid_python_code")));
        System.out.println("  [Python errors count] = " + r.errors().size());

        subsection("Jinja: قالب valid.jinja الصحيح (extends موجود)");
        // ملاحظة: valid.jinja يستخدم extends → base_valid.jinja موجود فعلياً
        JinjaAnalysisResult jValid;
        try {
            jValid = analyzeJinja(
                    "tests/test7_all_semantic_checks/templates/valid.jinja",
                    Set.of("items", "title", "user"),
                    Map.of(
                            "items", List.of("قلم", "كتاب"),
                            "title", "اختبار",
                            "user", Map.of("name", "أحمد", "age", 30)
                    )
            );
            System.out.println("  [Jinja valid errors]   = " + jValid.errors().size());
            System.out.println("  [Jinja valid warnings] = " + jValid.warnings().size());

            // لا يجب أن يكون هناك خطأ "لم يُمرَّر" لـ items أو title أو user
            check("لا خطأ 'لم يُمرَّر' لـ items/title/user في valid.jinja",
                    jValid.errors().stream().noneMatch(e ->
                            e.toString().contains("items") ||
                            e.toString().contains("title") ||
                            e.toString().contains("user")));

            // الـ blocks (title, content) يجب ألا تُسبب أخطاء
            check("لا خطأ 'لم يُمرَّر' لأسماء الـ blocks",
                    jValid.errors().stream().noneMatch(e ->
                            e.toString().contains("block")));
        } catch (Exception ex) {
            System.out.println("  [تحذير] فشل تحليل valid.jinja: " + ex.getMessage());
            check("valid.jinja يُحلَّل بدون استثناء", false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  مساعد: هل تحتوي القائمة على خطأ يتضمن أياً من الكلمات المفتاحية؟
    // ══════════════════════════════════════════════════════════════════════════
    private static boolean containsAny(List<SemanticError> list, String... keywords) {
        return list.stream().anyMatch(e -> {
            String s = e.toString().toLowerCase();
            for (String kw : keywords) {
                if (s.contains(kw.toLowerCase())) return true;
            }
            return false;
        });
    }
}
