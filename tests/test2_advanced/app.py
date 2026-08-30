from flask import Flask, render_template

app = Flask(__name__)

@app.route("/")
def index():
    course_name = "Compiler Construction"
    students = [
        {"name": "Omar", "grade": 95},
        {"name": "Saif", "grade": 92},
        {"name": "Abdalrhmman", "grade": 90},
        {"name": "Mohab", "grade": 88},
        {"name": "Unknown", "grade": 40}
    ]
    return render_template("grades.jinja", course_name=course_name, students=students)

if __name__ == "__main__":
    app.run()
