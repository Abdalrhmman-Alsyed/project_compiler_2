from flask import Flask, render_template

app = Flask(__name__)

@app.route("/")
def index():
    users = [
        {"name": "Alice", "role": "Admin", "active": True},
        {"name": "Bob", "role": "Editor", "active": False},
        {"name": "Charlie", "role": "Viewer", "active": True}
    ]
    return render_template("comprehensive.jinja", title="Comprehensive Test", users=users)

if __name__ == "__main__":
    app.run(debug=True)
