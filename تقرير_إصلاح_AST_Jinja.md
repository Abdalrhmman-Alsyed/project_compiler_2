# تقرير إصلاح بناء AST لـ Jinja2

**المشروع:** `E:\project_compiler_2`  
**التاريخ:** 25 آب 2026  
**الهدف:** جعل شجرة التركيب المجردة لقوالب Jinja/HTML تُبنى بشكل صحيح (تداخل الأجسام، التعبيرات المركبة، الاستدعاءات).

---

## الخلاصة

قبل الإصلاح كانت عقد Jinja تُنشأ، لكن الشجرة كانت **مسطّحة وخاطئة**: أجسام `{% if %}` / `{% for %}` / `{% block %}` فارغة، و`product.name` لا يصبح وصول خاصية، والتعبيرات الثنائية تُفقد.

بعد الإصلاح، قالب `test/jinja/index.html` يُنتج شجرة متداخلة صحيحة، وفحوصات التحليل الدلالي العشرة ما زالت تعمل على `error_demo.html`.

---

## الخطوة 1 — تشخيص المشكلة (قبل التعديل)

راجعت ثلاثة مصادر معاً:

1. `flaskTemplate/FlaskTemplateParser.g4`
2. `src/ast/visitors/TemplateASTBuilder.java`
3. عقد AST الموجودة لكن غير المستخدمة (`BinaryExpressionNode`, `AttributeAccessNode`)

العيوب الأساسية التي ثبتت:

| العيب | الأثر |
|--------|--------|
| `{% if cond %}` ينتهي عند `%}` والجسم قاعدة اختيارية داخل الوسم | جسم الـ if/for/block يبقى فارغاً، والمحتوى يظهر كأخوة |
| `{% elif %}` كان يطلب `endif` داخل نفس الوسم | فشل نحوي أو تجاهل |
| `{% endif %}` / `{% endfor %}` / `{% endblock %}` بلا visitor | تُحذف (`null`) |
| لا زيارة لمستويات `or` / `and` / `==` / `+` | `BinaryExpressionNode` لا يُنشأ؛ يبقى آخر معامل فقط |
| `EXPR_ID ('.' EXPR_ID)*` يُمرَّر عبر `toString()` للقائمة | `product.name` لا يصبح `AttributeAccessNode` |
| لا تغليف لـ `{{ }}` في جسم القالب | تعبير عارٍ بدل `JinjaExpressionNode` |
| `argumentList` لا يدعم `name=value` | `url_for(..., product_id=...)` يُسطَّح |
| لا عقدة `macro` | `{% macro %}` خارج الشجرة |
| تعليقات `{# #}` تبقى توكنات | قد تكسر التحليل النحوي |
| `getChildren()` في if/for/with يرجع قائمة فارغة | أي walker عام لا يرى الأبناء |

---

## الخطوة 2 — تعديل الـ Lexer

**الملف:** `flaskTemplate/FlaskLexer.g4`

ما تم:

1. إضافة الكلمتين المفتاحيتين `macro` و `endmacro` **قبل** `BLOCK_ID` حتى لا تُعاملان كمعرّفات عادية.
2. تخطي تعليقات Jinja بالكامل حتى لا تظهر في جدول التوكنات:
   - `{#` → `skip` ثم دخول وضع التعليق
   - `#}` → `skip` والخروج من الوضع
   - باقي أحرف التعليق → `skip`
3. نفس التخطي لتعليقات الصفات `ATTR_JINJA_COMMENT_START`.

بدون هذه الخطوة، `{% macro foo() %}` كان سيُفسَّر كـ `genericBlock` باسم `macro`.

---

## الخطوة 3 — إعادة كتابة قواعد كتل Jinja

**الملف:** `flaskTemplate/FlaskTemplateParser.g4`

التصميم الجديد: كل تركيب يستهلك **وسم الافتتاح + الجسم + وسم الإغلاق** كقاعدة واحدة.

مثال `{% if %}`:

```
ifBlock
    : {% if cond %}
      templateContent
      elifBlock*
      elseBlock?
      {% endif %}
```

وبالمثل:

- `forBlock`: `{% for x in expr %} ... {% else %}? {% endfor %}`
- `namedBlock`: `{% block name %} ... {% endblock %}`
- `withBlock`: `{% with expr %} ... {% endwith %}`
- `macroBlock`: `{% macro name(params) %} ... {% endmacro %}`
- الوسوم بلا جسم بقيت وسمة واحدة: `set` / `include` / `import` / `from import` / `extends` / `generic`

النتيجة: محتوى `{% if products %}` يصبح **أبناء** لـ `IfBlockNode` وليس أشقاء بعده.

كما فُصلت عبارات الصفات `attrBlockStatement` (`set` / `include` / generic) لأن `{% if %}` الكامل لا معنى له داخل قيمة صفة HTML.

---

## الخطوة 4 — إعادة كتابة قواعد التعبيرات

**نفس الملف:** `FlaskTemplateParser.g4`

### في وضع `{{ }}`

- المعرّف أصبح `EXPR_ID` فقط (بدون نقاط مدمجة).
- النقاط والاستدعاءات والفلاتر صارت **لاحقات (postfix)**:
  - `.name` → `memberOp`
  - `(args)` → `callExpr`
  - `| filter` → `filterExpr`
- الوسائط أصبحت:
  - `name=value` → `keywordArg`
  - تعبير عادي → `positionalArg`
- العامل الأحادي أصبح تراجعياً: `not not x` يعمل.

### في وضع `{% %}`

- نفس فكرة اللاحقات: `blockMemberOp` / `blockCallOp` / `blockFilterOp`.
- وسائط مسمّاة في البلوك: `blockKeywordArg`.
- أقواس `(expr)` تُزار صراحة بدل أن يرجع الزائر الافتراضي توكن `)`.

---

## الخطوة 5 — توسيع عقد AST

ملفات جديدة/معدَّلة:

| الملف | التغيير |
|--------|---------|
| `MacroBlockNode.java` | عقدة جديدة: اسم الماكرو + المعاملات + الجسم |
| `NodeKind.java` | إضافة `JINJA_MACRO_BLOCK` |
| `IfBlockNode.java` | قائمة `elif` + `else` كأبناء، و`getChildren()` يعيد الشرط والمحتوى والفروع |
| `ForBlockNode.java` | `getChildren()` يعيد الـ iterable والجسم والـ else |
| `ElifBlockNode` / `WithBlockNode` / `BlockBlockNode` / `ElseBlockNode` / `GenericBlockNode` | `getChildren()` يعيد المحتوى الفعلي |
| `JinjaBlockNode.java` | تنفيذ افتراضي لـ `getChildren()` من قائمة المحتوى |
| `CallExpressionNode.java` | قائمة موازية `keywordNames` للوسائط المسمّاة |
| `TemplateASTVisitor.java` | `visit(MacroBlockNode)` |

---

## الخطوة 6 — إعادة بناء `TemplateASTBuilder`

**الملف:** `src/ast/visitors/TemplateASTBuilder.java`

ما يفعله الزائر الآن:

1. **`visitIfStart`**: يبني الشرط، يضيف محتوى الجسم، يزور كل `elif`، يربط `else`.
2. **`visitForStart`**: يربط الجسم و`{% else %}` الاختياري داخل عقدة الـ for.
3. **`visitBlockStart` / `visitWithStart` / `visitMacroBlock`**: يملأ الجسم من `templateContent`.
4. **`visitJinjaExprNode`**: يغلّف `{{ }}` في `JinjaExpressionNode`.
5. **`foldBinary`**: يطوي `a or b and c` و`==` و`>` و`+` و`*` إلى `BinaryExpressionNode` من اليسار لليمين، في وضعي `{{ }}` و`{% %}`.
6. **`applyExprPostfixes` / `applyBlockPostfixes`**: يبني سلسلة
   `Variable(product)` → `AttributeAccess(.name)` → اختيارياً فلتر أو استدعاء.
7. **وسائط الاستدعاء**: `addKeywordArgument("product_id", ...)`.
8. **`visitHtmlText`**: يضمّ توكنات `HTML_TEXT+` بـ `getText()` بدل `List.toString()` (الذي كان ينتج `[نص]`).
9. **قوائم/قواميس/استدعاءات فارغة**: لا ترمي `NullPointerException`.
10. **`visitNormalElement`**: يتحمّل عنصراً بلا محتوى (`<div></div>`).

حُذف `visitJinjaBlockNode` القديم لأن `jinjaBlock` لم يعد وسمة واحدة ملفوفة.

---

## الخطوة 7 — الطباعة والتحليل الدلالي وجدول الرموز

### `PrintASTVisitor.java`

- يطبع فروع `elif`/`else` تحت الـ if.
- يطبع `MacroBlockNode`.
- يطبع الوسائط المسمّاة `KWARG name=`.
- أُزيلت حيلة حذف الأقواس المربعة `[...]` من النص؛ السبب الأصلي (قائمة توكنات) أُصلح.

### `JinjaSemanticAnalyzer.java`

- لم يعد هناك قاعدة مستقلة `{% endfor %}`، لذلك:
  - الدخول لنطاق متغير الحلقة في `visitForStart`
  - زيارة الجسم (`super.visit`)
  - ثم إخراج المتغير وإضافته إلى المنتهية صلاحيتها
- هذا يحافظ على الفحص 5: استخدام متغير `for` بعد الحلقة.
- `visitIdentifierExpr` يقرأ `EXPR_ID()` المفرد بدل `EXPR_ID(0)`.

### `JinjaSymbolTableBuilder.java`

- `visitCallExpr` لم يعد يعتمد على `EXPR_ID()` داخل الاستدعاء (الاسم صار في الـ atom السابق).
- `visitBlockStart` / `visitForStart` / `visitMacroBlock` يزورون الجسم الآن لأن الجسم ابن في شجرة التحليل.

---

## الخطوة 8 — توليد المحلّل والترجمة والتحقق

1. تنزيل `libs/antlr-4.13.2-complete.jar` (يتوقعه تطبيق Flask أصلاً).
2. توليد الملفات إلى `src/gen` بأمر ANTLR 4.13.2 مع `-Xexact-output-dir` و`-visitor`.
3. نسخ `FlaskLexer.tokens` إلى `flaskTemplate/` حتى يجد الـ parser ملف `tokenVocab`.
4. ترجمة كل مصادر Java إلى `out/` بـ JDK 21 — نجحت بدون أخطاء.
5. تشغيل `TemplateASTTest` على `index.html`.
6. تشغيل `Main` على المسار الكامل بما فيه `error_demo`.

---

## نتيجة التحقق على `index.html`

الشجرة الناتجة (مختصرة) تطابق التداخل المتوقع:

```
TemplateRoot
  Doctype
  HTMLDocument
    Extends("base.html")
    Block("title")
      HTMLText "قائمة المنتجات"
    Block("content")
      h2 "قائمة المنتجات"
      If(products)
        CONTENT:
          div.product-grid
            For(product in products)
              div.product-card
                img src={{ product.image_url }}
                h3 {{ product.name }}          ← JinjaExpression + AttributeAccess
                a href={{ url_for(..., product_id=product.id) }}
                    ← Call + KWARG product_id
        ELSE:
          p.no-products
```

نقاط تأكدت عملياً من الخرج:

- جسم `{% block content %}` لم يعد فارغاً.
- `{% for %}` ابن لـ `{% if %}` وابن لـ `div.product-grid`.
- `{% else %}` ابن للـ if وليس أخاً بعد `{% endif %}`.
- `product.name` / `product.price` / `product.image_url` / `product.id` كلها `AttributeAccessNode`.
- `url_for('product_detail', product_id=product.id)` استدعاء مع وسيط مسمّى.

---

## نتيجة التحقق على التحليل الدلالي (`error_demo.html`)

`Main` ما زال يُبلغ الفحوصات العشرة، منها:

- قالب يمتد لنفسه + `extends` أكثر من مرة
- `{% extends %}` ليس أول عبارة Jinja
- بلوك `content` مكرر
- فلتر `unknownFilter`
- `{% set products %}` يعيد تعريف متغير بايثون ثم يعيد تعريفه محلياً
- `item` مستخدم بعد `{% endfor %}`
- `{% for empty_item in [] %}`
- `{% include %}` لملف غير موجود

قوالب المتجر السليمة (`base`, `index`, `product_detail`, `add_product`) بلا قضايا دلالية.

---

## الملفات التي تغيّرت

```
flaskTemplate/FlaskLexer.g4
flaskTemplate/FlaskTemplateParser.g4
src/ast/visitors/TemplateASTBuilder.java
src/ast/visitors/PrintASTVisitor.java
src/ast/visitors/TemplateASTVisitor.java
src/ast/template/NodeKind.java
src/ast/template/jinja/blocks/JinjaBlockNode.java
src/ast/template/jinja/blocks/IfBlockNode.java
src/ast/template/jinja/blocks/ElifBlockNode.java
src/ast/template/jinja/blocks/ElseBlockNode.java
src/ast/template/jinja/blocks/ForBlockNode.java
src/ast/template/jinja/blocks/WithBlockNode.java
src/ast/template/jinja/blocks/BlockBlockNode.java
src/ast/template/jinja/blocks/GenericBlockNode.java
src/ast/template/jinja/blocks/MacroBlockNode.java          (جديد)
src/ast/template/jinja/expressions/CallExpressionNode.java
src/semantic/JinjaSemanticAnalyzer.java
src/symbolTableJinja/JinjaSymbolTableBuilder.java
```

ملفات مولَّدة (gitignored عادة): `src/gen/*`  
جار ANTLR المستخدم للبناء: `libs/antlr-4.13.2-complete.jar`

---

## ما لم يُغيَّر (خارج نطاق هذا الإصلاح)

- لا توليد شيفرة (code generation) — المشروع ما زال frontend.
- `HTMLDocumentNode.getChildren()` ما زال فارغاً (الطباعة تستخدم getters مباشرة).
- طباعة الصفات التي تحتوي `{{ }}` ما زالت موزَّعة على عدة أسطر في `PrintASTVisitor`؛ الشجرة نفسها صحيحة.
- تغطية اللغة الجزئية لبايثون لم تُمس.

---

## كيف تعيد البناء محلياً

من جذر المشروع، مع JDK 17+ وملف ANTLR في `libs/`:

```text
java -jar libs/antlr-4.13.2-complete.jar -o src/gen -Xexact-output-dir -visitor -no-listener flaskTemplate/FlaskLexer.g4
copy src\gen\FlaskLexer.tokens flaskTemplate\FlaskLexer.tokens
java -jar libs/antlr-4.13.2-complete.jar -o src/gen -Xexact-output-dir -visitor -no-listener -lib flaskTemplate flaskTemplate/FlaskPythonParser.g4
java -jar libs/antlr-4.13.2-complete.jar -o src/gen -Xexact-output-dir -visitor -no-listener -lib flaskTemplate flaskTemplate/FlaskTemplateParser.g4
javac -encoding UTF-8 -d out -cp libs/antlr-4.13.2-complete.jar  (كل ملفات src/**/*.java)
java -cp out;libs/antlr-4.13.2-complete.jar TemplateASTTest
java -cp out;libs/antlr-4.13.2-complete.jar Main
```

---

**النتيجة:** AST الخاص بـ Jinja أصبح يُبنى بشكل صحيح من البداية للنهاية على مستوى frontend: تداخل الكتل، وصول الخصائص، الاستدعاءات بالوسائط المسمّاة، والتعبيرات الثنائية.
