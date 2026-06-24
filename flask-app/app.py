from flask import Flask, render_template, request, redirect, url_for, flash, jsonify
from werkzeug.utils import secure_filename
import os, sys, re, subprocess, json, tempfile

app = Flask(__name__)
app.secret_key = 'compilers-2025-abdal-secret'

UPLOAD_FOLDER = 'static'
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}

# Root of the Java compiler project (one level up from flask-app/)
COMPILER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

# ── Mock Database ──────────────────────────────────────────────────────────────

PRODUCTS_BASE_DATA = [
    {
        'id': 1,
        'name': 'هاتف ذكي X (إصدار محدود)',
        'price': 799.99,
        'details': 'أقوى هاتف لعام 2025. يتميز بكاميرا 200MP ومعالج A15.',
        'image_filename': 'default.png'
    },
    {
        'id': 2,
        'name': 'سماعات بلوتوث Pro',
        'price': 149.00,
        'details': 'جودة صوت عالية وإلغاء ضوضاء فعال لمدة 30 ساعة متواصلة.',
        'image_filename': 'default.png'
    }
]

products_data = []
next_id = 3


def initialize_products_data():
    global products_data, next_id
    products_data.clear()
    for p in PRODUCTS_BASE_DATA:
        products_data.append(p.copy())
    if PRODUCTS_BASE_DATA:
        next_id = max(p['id'] for p in PRODUCTS_BASE_DATA) + 1
    else:
        next_id = 1


initialize_products_data()

# ── Helpers ────────────────────────────────────────────────────────────────────

def get_image_url(filename):
    return url_for('static', filename=filename)


def _parse_issues(text, start_marker, end_marker=None):
    """Extract [ERROR]/[WARNING] lines strictly between two section markers."""
    pattern = re.compile(
        r'\[(ERROR|WARNING)\s*\]\s+Line\s+(\d+):(\d+)\s+.\s*(.+)',
        re.UNICODE
    )
    start_idx = text.find(start_marker)
    if start_idx == -1:
        return []
    if end_marker:
        end_idx = text.find(end_marker, start_idx + len(start_marker))
        section = text[start_idx:end_idx] if end_idx != -1 else text[start_idx:]
    else:
        section = text[start_idx:]

    issues = []
    for line in section.splitlines():
        m = pattern.search(line)
        if m:
            issues.append({
                'type': m.group(1).strip(),
                'line': int(m.group(2)),
                'col':  int(m.group(3)),
                'msg':  m.group(4).strip(),
            })
    return issues


def _parse_bridge(text):
    """Extract Generator bridge rows from printReport() output.
    Format:  '  index.html                → products'
    """
    bridge = []
    start = text.find('Variables extracted from render_template()')
    if start == -1:
        return bridge
    section = text[start:start + 800]
    # Each line: optional spaces, template name (.html), whitespace, arrow-char, whitespace, vars
    row_re = re.compile(r'(\S+\.html)\s+\S+\s+(.+)', re.UNICODE)
    for line in section.splitlines():
        m = row_re.search(line)
        if m:
            tmpl = m.group(1).strip()
            raw  = m.group(2).strip()
            if raw == '(no variables)':
                vars_ = []
            else:
                vars_ = [v.strip() for v in raw.split(',') if v.strip()]
            bridge.append({'template': tmpl, 'vars': vars_})
    return bridge

def _parse_ast(text):
    """Extract Python AST tree section from Main.java output."""
    marker = '--- AST ---'
    start = text.find(marker)
    if start == -1:
        return ''
    content_start = start + len(marker)
    next_marker = text.find('\n---', content_start)
    section = text[content_start:next_marker].strip() if next_marker != -1 else text[content_start:].strip()
    lines = section.splitlines()
    if len(lines) > 300:
        section = '\n'.join(lines[:300]) + f'\n... (+{len(lines)-300} سطر إضافي)'
    return section


def _parse_symbol_table(text):
    """Extract Python Symbol Table section from Main.java output."""
    marker = '--- Symbol Table ---'
    start = text.find(marker)
    if start == -1:
        return ''
    content_start = start + len(marker)
    # The symbol table content itself starts with '===...DETAILED SCOPE TREE...'
    # The section ends when the Python Semantic Analysis block begins.
    end = -1
    for end_marker in ('PYTHON SEMANTIC', 'SEMANTIC ERROR DEMO', 'GENERATOR:'):
        pos = text.find(end_marker, content_start)
        if pos != -1 and (end == -1 or pos < end):
            end = pos
    section = text[content_start:end].strip() if end != -1 else text[content_start:].strip()
    lines = section.splitlines()
    if len(lines) > 200:
        section = '\n'.join(lines[:200]) + f'\n... (+{len(lines)-200} سطر إضافي)'
    return section


# ── Routes: Store ──────────────────────────────────────────────────────────────

@app.route('/')
@app.route('/products')
def product_list():
    enriched = []
    for p in products_data:
        ep = p.copy()
        ep['image_url'] = get_image_url(p['image_filename'])
        enriched.append(ep)
    return render_template('index.html', products=enriched)


@app.route('/products/<int:product_id>')
def product_detail(product_id):
    product = next((p for p in products_data if p['id'] == product_id), None)
    if product is None:
        return "Product Not Found", 404
    ep = product.copy()
    ep['image_url'] = get_image_url(product['image_filename'])
    return render_template('product_detail.html', product=ep)


@app.route('/add', methods=['GET', 'POST'])
def add_product():
    global next_id
    if request.method == 'POST':
        image_filename = 'default.png'
        if 'image_file' in request.files:
            file = request.files['image_file']
            if file and file.filename:
                filename = secure_filename(file.filename)
                file.save(os.path.join(UPLOAD_FOLDER, filename))
                image_filename = filename

        name = request.form['name']
        products_data.append({
            'id':             next_id,
            'name':           name,
            'price':          float(request.form['price']),
            'details':        request.form['details'],
            'image_filename': image_filename,
        })
        next_id += 1
        flash(f'success:تمت إضافة «{name}» بنجاح!')
        return redirect(url_for('product_list'))

    return render_template('add_product.html')


@app.route('/delete/<int:product_id>', methods=['POST'])
def delete_product(product_id):
    global products_data
    target = next((p for p in products_data if p['id'] == product_id), None)
    if target:
        product_name = target['name']
        fn = target.get('image_filename', '')
        if fn and fn not in ('default.png', 'default.jpg'):
            fp = os.path.join(UPLOAD_FOLDER, fn)
            if os.path.exists(fp):
                os.remove(fp)
        products_data = [p for p in products_data if p['id'] != product_id]
        flash(f'danger:تم حذف «{product_name}» بنجاح!')
    return redirect(url_for('product_list'))

# ── Route: Compiler ────────────────────────────────────────────────────────────

@app.route('/compiler', methods=['GET', 'POST'])
def compiler_page():
    if request.method == 'GET':
        return render_template('compiler.html', ran=False, ast_text='', symbol_table_text='')

    # Run the Java compiler
    sep = ';' if sys.platform == 'win32' else ':'
    cp  = f'out{sep}libs/antlr-4.13.2-complete.jar'

    error_msg         = None
    python_errors     = []
    python_warnings   = []
    jinja_errors      = []
    jinja_warnings    = []
    bridge            = []
    ast_text          = ''
    symbol_table_text = ''

    try:
        result = subprocess.run(
            ['java', '-Dfile.encoding=UTF-8', '-cp', cp, 'Main'],
            capture_output=True,
            cwd=COMPILER_ROOT,
            timeout=60
        )
        output = result.stdout.decode('utf-8', errors='replace')
        stderr = result.stderr.decode('utf-8', errors='replace')
        full   = output + '\n' + stderr

        # Split output into Python demo and Jinja2 demo sections by index.
        # 'SEMANTIC ERROR DEMO' appears twice: first=Python, second=Jinja2.
        idx1 = full.find('SEMANTIC ERROR DEMO')
        idx2 = full.find('SEMANTIC ERROR DEMO', idx1 + 1) if idx1 != -1 else -1

        py_section  = full[idx1:idx2] if (idx1 != -1 and idx2 != -1) else ''
        j2_section  = full[idx2:]     if idx2 != -1                   else ''

        py_issues       = _parse_issues(py_section, 'SEMANTIC ERROR DEMO')
        python_errors   = [i for i in py_issues if i['type'] == 'ERROR']
        python_warnings = [i for i in py_issues if i['type'] == 'WARNING']

        j2_issues       = _parse_issues(j2_section, 'SEMANTIC ERROR DEMO')
        jinja_errors    = [i for i in j2_issues if i['type'] == 'ERROR']
        jinja_warnings  = [i for i in j2_issues if i['type'] == 'WARNING']

        # Parse Generator bridge
        bridge = _parse_bridge(full)

        # Parse AST tree and Symbol Table from full output
        # (both sections appear before SEMANTIC ERROR DEMO, not inside py_section)
        ast_text          = _parse_ast(full)
        symbol_table_text = _parse_symbol_table(full)

    except FileNotFoundError:
        error_msg = "تعذّر العثور على Java — تأكد أن Java 17+ مثبتة وموجودة في PATH"
    except subprocess.TimeoutExpired:
        error_msg = "انتهت مهلة تشغيل المترجم (60 ثانية)"
    except Exception as e:
        error_msg = str(e)

    return render_template(
        'compiler.html',
        ran               = True,
        error_msg         = error_msg,
        python_errors     = python_errors,
        python_warnings   = python_warnings,
        jinja_errors      = jinja_errors,
        jinja_warnings    = jinja_warnings,
        bridge            = bridge,
        ast_text          = ast_text,
        symbol_table_text = symbol_table_text,
    )


@app.route('/api/analyze', methods=['POST'])
def api_analyze():
    data     = request.get_json(force=True) or {}
    code     = (data.get('code') or '').strip()
    lang     = data.get('lang', 'python').lower()

    if not code:
        return jsonify({'errors': [], 'warnings': []})

    suffix = '.txt' if lang == 'python' else '.html'
    fd, temp_path = tempfile.mkstemp(suffix=suffix)

    # The Jinja2 grammar requires real HTML content before the first {% %} tag.
    # Prepend a 2-line HTML header so the parser works, then subtract those
    # lines from every reported line number so they match the user's input.
    JINJA_HTML_PREFIX = '<!DOCTYPE html>\n<html>\n'
    jinja_offset = JINJA_HTML_PREFIX.count('\n') if lang == 'jinja' else 0

    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            f.write(JINJA_HTML_PREFIX + code if lang == 'jinja' else code)

        sep = ';' if sys.platform == 'win32' else ':'
        cp  = f'out{sep}libs/antlr-4.13.2-complete.jar'
        result = subprocess.run(
            ['java', '-Dfile.encoding=UTF-8', '-cp', cp, 'LiveAnalyzer', lang, temp_path],
            capture_output=True, cwd=COMPILER_ROOT, timeout=30
        )
        output = result.stdout.decode('utf-8', errors='replace').strip()
        for line in reversed(output.splitlines()):
            line = line.strip()
            if line.startswith('{'):
                data = json.loads(line)
                if jinja_offset:
                    for key in ('errors', 'warnings'):
                        for issue in data.get(key, []):
                            issue['line'] = max(1, issue['line'] - jinja_offset)
                return jsonify(data)
        return jsonify({'errors': [], 'warnings': []})
    except subprocess.TimeoutExpired:
        return jsonify({'errors': [], 'warnings': [], 'parseError': 'انتهت المهلة (30s)'})
    except Exception as e:
        return jsonify({'errors': [], 'warnings': [], 'parseError': str(e)})
    finally:
        try:
            os.unlink(temp_path)
        except OSError:
            pass


if __name__ == '__main__':
    app.run(debug=True, port=5000)
