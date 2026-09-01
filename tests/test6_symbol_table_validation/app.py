from flask import Flask, render_template, request, redirect, url_for

app = Flask(__name__)

# ── اختبار 1: متغيرات عالمية بأنواع واضحة ──────────────────────────────
counter = 0                          # int
name = "Project"                     # str
prices = [10.5, 20.0, 30.99]        # list
config = {"debug": True, "port": 8080}  # dict
tags = {"python", "flask", "jinja"}  # set

# ── اختبار 2: دالة بدون أخطاء ─────────────────────────────────────────
def home():
    users = ["Alice", "Bob", "Carol"]
    total = 0
    for u in users:
        total = total + 1
    return render_template("home.jinja", users=users, count=total)

# ── اختبار 3: نطاق For وإعادة الاستخدام بعده (Block Scoping) ──────────
def scope_test():
    items = [1, 2, 3]
    for item in items:
        processed = item * 2
    # في مترجمنا: processed خارج نطاق الـ for لذا سيكون خطأ (Block Scoping)
    return render_template("scope.jinja", items=items)

# ── اختبار 4: نطاقات متداخلة ────────────────────────────────────────────
def nested_scopes():
    result = []
    for i in [1, 2, 3]:
        for j in [10, 20]:
            value = i + j
    return render_template("nested.jinja", result=result)

# ── اختبار 5: استخدام متغير global داخل دالة ───────────────────────────
def update_counter():
    global counter
    counter = counter + 1
    return redirect(url_for("home"))

# ── اختبار 6: دالة تستقبل معاملات متعددة ──────────────────────────────
def product_page(product_id, category, page=1):
    product = {"id": product_id, "name": "Test", "price": 9.99}
    return render_template("product.jinja", product=product, category=category, page=page)

# ── اختبار 7: try/except مع متغير استثناء ──────────────────────────────
def safe_divide(a, b):
    try:
        result = a / b
    except ZeroDivisionError as e:
        result = 0
    return result

if __name__ == "__main__":
    app.run(debug=True)
