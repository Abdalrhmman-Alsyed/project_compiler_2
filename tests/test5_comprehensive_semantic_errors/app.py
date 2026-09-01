import os
import os # تحذير 9: استيراد وحدة تم استيرادها مسبقا

# خطأ 1: استخدام return خارج دالة
return 10

# خطأ 2: استخدام break خارج حلقة تكرار
break

# خطأ 3: استخدام continue خارج حلقة تكرار
continue

# خطأ 21: متغير غير معرف
print(undefined_global_var)

def my_func():
    # تحذير 14: دالة تحتوي على جسم فارغ (فقط pass)
    pass

# خطأ 4: دالة تم تعريفها اكثر من مرة
def my_func():
    pass

# خطأ 5: دالة تحتوي على معاملات مكررة
# تحذير 16: دالة تحتوي على أكثر من 7 معاملات
def complex_func(a, b, c, d, e, f, g, h, a):
    # خطأ 8: كود غير قابل للوصول بعد تعليمة return
    return 1
    print("unreachable")

def variable_scope_test():
    # تحذير 15: تم استخدام الاسم قبل اسناده في النطاق المحلي
    print(local_var)
    local_var = 5
    
    # خطأ 6: التصريح عن global بعد استخدام نفس الاسم محلياً
    global local_var
    
    # تحذير 12: متغير محلي تم تعريفه ولم يستخدم ابدا
    unused_var = 100

def outer_function():
    outer_var = 10
    
def inner_function():
    # خطأ 22: المتغير خارج النطاق (معرف في دالة اخرى وليس عالمي)
    print(outer_var)

# تحذير 10: هذا الاسناد يكتب فوق الدالة my_func
my_func = "overwritten"

# تحذير 11: اسناد قيمة يخفي دالة مبنية مسبقا
len = 5

# خطأ 7: قسمة على الصفر
x = 10 / 0
y = 20 // 0

# تحذير 17: مقارنة مع None باستخدام == بدلا من is
if x == None:
    pass

# تحذير 18: مقارنة مع True باستخدام !=
if y != True:
    pass

# خطأ 19: عدم تطابق الانواع (عملية جمع بين نص ورقم)
invalid_sum = "string" + 5

# خطأ 20: تكرار على كائن غير قابل للتكرار (رقم)
for i in 55:
    pass

# تحذير 13: استدعاء اسم لا يمكن حله (متغير غير معرف كدالة)
unknown_function()
