from flask import Flask, render_template

app = Flask(__name__)

# قائمة (List) للوصول عبر الفهرس (Index)
my_list = ["أول عنصر", "ثاني عنصر", "ثالث عنصر"]

# قاموس (Dictionary) للوصول عبر المفتاح (Key) أو الخاصية (Attribute)
my_dict = {
    "title": "تجربة توليد الكود",
    "nested": {
        "value": 99
    }
}

@app.route("/")
def home():
    return render_template("index.jinja", data_list=my_list, my_dict=my_dict)

if __name__ == "__main__":
    app.run(debug=True)
