fake_users = [
    {"id": 100, "username": "saif", "email": "saif@example.com"},
    {"id": 101, "username": "ahmed", "email": "ahmed@example.com"}
]

def render_users():
    return render_template("test_mock.jinja", users=fake_users)
