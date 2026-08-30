from flask import Flask, render_template

app = Flask(__name__)

@app.route("/")
def index():
    title = "Hello Basic Compiler"
    user = "Saif"
    return render_template("index.jinja", title=title, user=user)

if __name__ == "__main__":
    app.run()
