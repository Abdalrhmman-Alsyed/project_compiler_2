from flask import Flask, render_template, request

app = Flask(__name__)

# ══════════════════════════════════════════════════════════════════════
#  أخطاء بايثون الدلالية (Python Semantic ERRORS)
# ══════════════════════════════════════════════════════════════════════

# ── خطأ 1: استيراد مكرر (سيأتي تحذير لا خطأ)
import os
import os  # تحذير: الوحدة 'os' تم استيرادها أكثر من مرة

# ── خطأ 2: استخدام return خارج دالة
return 42

# ── خطأ 3: استخدام break خارج حلقة تكرار
break

# ── خطأ 4: استخدام continue خارج حلقة تكرار
continue

# ── خطأ 5: تعريف الدالة أكثر من مرة بنفس الاسم
def duplicate_func():
    pass

def duplicate_func():   # خطأ: الدالة duplicate_func تم تعريفها أكثر من مرة
    return 1

# ── خطأ 6: تكرار اسم المعامل في نفس الدالة
# ── + تحذير: دالة تحتوي على جسم فارغ (pass)
def bad_params(x, y, x):   # خطأ: المعامل x مكرر
    pass

# ── خطأ 7: الإعلان عن global بعد إسناد محلي
def global_after_local():
    counter = 0          # إسناد محلي أولاً
    global counter       # خطأ: global بعد إسناد محلي

# ── خطأ 8: قسمة على صفر صريحة
def div_errors():
    a = 10 / 0           # خطأ: قسمة على الصفر
    b = 20 // 0          # خطأ: قسمة صحيحة على الصفر

# ── خطأ 9: كود غير قابل للوصول بعد return
def unreachable_code():
    return "done"
    print("هذا السطر لا يمكن الوصول إليه")   # خطأ: كود غير قابل للوصول

# ── خطأ 10: عدم تطابق الأنواع (Type Mismatch) — جمع نص وعدد
def type_errors():
    bad = "نص" + 5       # خطأ: أنواع غير متوافقة '+': 'str' و 'int'

# ── خطأ 11: دوران على كائن غير قابل للتكرار
def not_iterable_error():
    for i in 99:         # خطأ: العدد الصحيح ليس قابلاً للتكرار
        pass

# ── خطأ 12: متغير غير معرّف في النطاق العام
print(totally_unknown_var)   # خطأ: المتغير غير معرّف

# ── خطأ 13: متغير خارج النطاق (معرّف في دالة أخرى)
def scope_owner():
    my_private_var = "أنا ملك لهذه الدالة"

def scope_thief():
    print(my_private_var)   # خطأ: المتغير خارج النطاق

# ══════════════════════════════════════════════════════════════════════
#  تحذيرات بايثون الدلالية (Python Semantic WARNINGS)
# ══════════════════════════════════════════════════════════════════════

# ── تحذير 1: الاستيراد المكرر (سبق تعريفه في الأعلى مع import os)

# ── تحذير 2: إسناد يكتب فوق اسم دالة
def my_overridable_func():
    return "original"

my_overridable_func = "تم الكتابة فوق الدالة"   # تحذير: يكتب فوق الدالة

# ── تحذير 3: متغير محلي معرّف ولم يُستخدم
def func_with_unused():
    unused_local = 999       # تحذير: معرّف ولم يُستخدم أبداً
    return "بدون استخدام unused_local"

# ── تحذير 4: استدعاء دالة لا يمكن الوصول إليها (غير معرّفة)
call_to_ghost_function()    # تحذير: لا يمكن تحليل الاسم

# ── تحذير 5: دالة فارغة (فقط pass) — تم أعلاه في duplicate_func الأولى و bad_params

# ── تحذير 6: استخدام اسم قبل الإسناد إليه في النطاق المحلي
def use_before_assign():
    print(future_var)       # تحذير: استخدام قبل الإسناد
    future_var = 10

# ── تحذير 7: دالة تحتوي على أكثر من 7 معاملات
def too_many_params(a, b, c, d, e, f, g, h):   # تحذير: 8 معاملات > 7
    pass

# ── تحذير 8: مقارنة مع None باستخدام == بدلاً من is
def compare_none(val):
    if val == None:          # تحذير: استخدم 'is None' بدلاً من '== None'
        pass
    if val != None:          # تحذير: استخدم 'is not None' بدلاً من '!= None'
        pass

# ── تحذير 9: مقارنة مع True/False باستخدام == بدلاً من التحقق المباشر
def compare_bool(flag):
    if flag == True:         # تحذير: استخدم 'if flag:' مباشرة
        pass
    if flag != False:        # تحذير: استخدم 'if flag:' مباشرة
        pass

# ── تحذير 10: إخفاء دالة مبنية مسبقاً (builtin shadowing)
len = 100                   # تحذير: يخفي الدالة المبنية مسبقاً 'len'

# ══════════════════════════════════════════════════════════════════════
#  كود صحيح لا يجب أن ينتج أي خطأ
# ══════════════════════════════════════════════════════════════════════

def valid_python_code():
    """دالة صحيحة تماماً لا يجب أن يُبلَّغ عنها"""
    numbers = [1, 2, 3, 4, 5]
    total = 0
    for n in numbers:
        total = total + n
    return total


@app.route("/")
def home():
    items = ["قلم", "كتاب", "حاسوب"]
    title = "الصفحة الرئيسية"
    user = {"name": "أحمد", "age": 30}
    return render_template("valid.jinja",
                           items=items, title=title, user=user)


@app.route("/errors")
def error_page():
    items = ["أ", "ب", "ج"]
    mock_list = [{"name": "عنصر 1"}, {"name": "عنصر 2"}]
    mock_dict = {"key": "value", "nested": {"inner_key": "inner_val"}}
    return render_template("errors.jinja",
                           items=items, mock_list=mock_list, mock_dict=mock_dict)


if __name__ == "__main__":
    app.run(debug=True)
