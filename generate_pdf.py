from weasyprint import HTML, CSS

html_content = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <title>تقرير الأخطاء الدلالية (Semantic Errors)</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 40px;
            color: #333;
            line-height: 1.6;
        }
        h1 {
            color: #2c3e50;
            border-bottom: 2px solid #3498db;
            padding-bottom: 10px;
        }
        h2 {
            color: #2980b9;
            margin-top: 30px;
        }
        ul {
            list-style-type: square;
        }
        li {
            margin-bottom: 10px;
        }
        .code-box {
            background-color: #f8f9fa;
            border-right: 4px solid #e74c3c;
            padding: 10px;
            font-family: monospace;
            margin-bottom: 20px;
            direction: ltr;
            text-align: left;
        }
    </style>
</head>
<body>
    <h1>تقرير المحلل الدلالي (Semantic Analyzer)</h1>
    <p>هذا التقرير يوضح جميع الأخطاء المنطقية التي يتم التقاطها ومعالجتها بواسطة الكومبايلر الخاص بمشروعك (Project Compiler 2) لكل من لغتي بايثون وجينجا.</p>
    
    <h2>أولاً: أخطاء بايثون (Python)</h2>
    <ul>
        <li><strong>متغير خارج النطاق أو غير معرف:</strong> يتم التقاط استخدام متغيرات لم يتم تعريفها من قبل، أو متغيرات تم تعريفها داخل دوال أخرى.</li>
        <li><strong>كود ميت (Unreachable Code):</strong> أي أوامر برمجية تُكتب بعد جملة <code>return</code> داخل الدالة سيتم رصدها.</li>
        <li><strong>تكرار الدوال:</strong> يمنع الكومبايلر تعريف دالتين بنفس الاسم في نفس النطاق.</li>
        <li><strong>تكرار البارامترات:</strong> اكتشاف المتغيرات المكررة داخل معاملات الدوال.</li>
        <li><strong>return في المكان الخطأ:</strong> رصد استخدام جملة <code>return</code> خارج الدوال.</li>
        <li><strong>break و continue:</strong> التأكد من استخدامهما فقط داخل الحلقات التكرارية (Loops).</li>
        <li><strong>أخطاء الـ Global:</strong> منع إسناد قيمة لمتغير محلي ثم تعريفه كـ <code>global</code> لاحقاً.</li>
    </ul>

    <h2>ثانياً: أخطاء جينجا (Jinja2)</h2>
    <ul>
        <li><strong>تكرار البلوكات (Blocks):</strong> رصد استخدام <code>{% block X %}</code> بنفس الاسم أكثر من مرة في القالب.</li>
        <li><strong>تكرار الوراثة:</strong> منع استخدام <code>{% extends %}</code> أكثر من مرة واحدة في نفس الملف.</li>
        <li><strong>الوراثة الدائرية (Circular Dependency):</strong> اكتشاف خطأ وراثة القالب لنفسه.</li>
        <li><strong>متغيرات غير ممررة (Variables Not Passed):</strong> التأكد من أن أي متغير يُستخدم في قالب جينجا قد تم تمريره فعلياً من دالة <code>render_template</code> في بايثون.</li>
    </ul>

    <h2>ثالثاً: هيكلية تخزين الأخطاء</h2>
    <p>يتم التقاط وتخزين الأخطاء بطريقة منظمة جداً داخل الكومبايلر:</p>
    <ol>
        <li>توجد قائمة <code>List&lt;SemanticError&gt; errors</code> داخل كل كلاس محلل.</li>
        <li>عند اكتشاف خطأ، يتم استدعاء دالة <code>error(msg, line, col)</code>.</li>
        <li>تقوم الدالة بإنشاء كائن جديد يحفظ نوع الخطأ، الرسالة، ورقم السطر والعمود.</li>
        <li>في النهاية يتم عرض الأخطاء عبر دالة <code>printReport()</code> في الكونسول.</li>
    </ol>
</body>
</html>
"""

HTML(string=html_content).write_pdf("semantic_errors_report.pdf")
print("PDF generated successfully.")
