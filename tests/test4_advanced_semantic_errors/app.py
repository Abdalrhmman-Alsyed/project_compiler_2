from flask import Flask, render_template

# [تحذير] استيراد وحدة تم استيرادها مسبقاً (Duplicate import)
from flask import Flask

app = Flask(__name__)

# [خطأ] استخدام تعليمة return خارج دالة
return 5

# [خطأ] استخدام تعليمة break خارج حلقة تكرار
break

# [خطأ] استخدام تعليمة continue خارج حلقة تكرار
continue

# [خطأ] قسمة على صفر
bad_math = 10 / 0

# [خطأ] عدم توافق في الأنواع (Type Mismatch)
# نجمع رقم مع نص
x_val = 5
y_val = x_val + "hello"

# [خطأ] خطأ نوعي: الدوران على شيء غير قابل للتكرار (Not iterable)
for item in 100:
    pass

# [خطأ] متغير غير معرف (Undefined variable)
print(ghost_var)

# [تحذير] دالة تحتوي على معاملات كثيرة جداً (>7)
def overloaded_func(a, b, c, d, e, f, g, h):
    # [خطأ] كود غير قابل للوصول بعد return
    return a
    print("This is unreachable!")

# [خطأ] تكرار اسم الدالة (Duplicate function name)
def overloaded_func():
    pass

# [خطأ] تكرار اسم المعامل في نفس الدالة
def bad_params(x, x):
    pass

# [تحذير] دالة فارغة تحتوي فقط على pass
def empty_function():
    pass

def scoping_test():
    # [تحذير] تعريف متغير محلي ولم يتم استخدامه أبداً
    unused_var = 42

    # [خطأ] الإعلان عن global بعد إسناد محلي لنفس المتغير
    global_test = 1
    global global_test

    # [تحذير] استخدام متغير قبل إسناد قيمة له في النطاق المحلي
    print(local_unassigned)
    local_unassigned = 10

def out_of_scope_test():
    # [خطأ] استخدام متغير من دالة أخرى (Scope Error)
    print(unused_var)

# [تحذير] إخفاء دالة مبنية مسبقاً (Shadowing built-in)
len = 5

# [تحذير] الكتابة فوق اسم دالة موجودة
empty_function = "now I am a string"

# [تحذير] مقارنة خاطئة مع None (يجب استخدام is None)
if x_val == None:
    pass

# [تحذير] مقارنة خاطئة مع True (يجب استخدام if x_val:)
if x_val == True:
    pass

@app.route('/')
def home():
    # تمرير متغيرات سليمة لجينجا لاختبار أخطاء جينجا
    return render_template("advanced.jinja", title="Test", items=[1, 2, 3])

if __name__ == "__main__":
    app.run()
