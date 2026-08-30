import subprocess
with open('test/jinja/error_demo.html', 'w') as f:
    f.write('{{ url_for("static", filename="css/styles.css") }}')
subprocess.run(['java', '-cp', 'out/production/flask-compiler-python:libs/antlr-4.13.2-complete.jar', 'Main'])
