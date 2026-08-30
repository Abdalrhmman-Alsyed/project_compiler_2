from flask import Flask, render_template, url_for
from app import app, products_data
with app.app_context():
    html = render_template('index.jinja', products=products_data)
    for line in html.split('\n'):
        if 'src=' in line:
            print(line.strip())
