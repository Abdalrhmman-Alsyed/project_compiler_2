from flask import Flask, render_template, request, redirect, url_for
from werkzeug.utils import secure_filename
import os

app = Flask(__name__)

# ----------------------------------------------------------------------
# إعدادات التطبيق والملفات
# ----------------------------------------------------------------------

UPLOAD_FOLDER = 'static'
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}

app.config['SERVER_NAME'] = 'localhost:5000'

# ----------------------------------------------------------------------
# البيانات الأساسية (Mock Database)
# ----------------------------------------------------------------------

PRODUCTS_BASE_DATA = [
    {
        'id': 1,
        'name': 'Smartphone X (Limited Edition)',
        'price': 799.99,
        'details': 'Flagship phone for 2025. 200MP camera and A15 processor.',
        'image_filename': 'default.png'
    },
    {
        'id': 2,
        'name': 'Bluetooth Headphones Pro',
        'price': 149.00,
        'details': 'High-quality sound and noise cancellation for 30 hours.',
        'image_filename': 'default.png'
    }
]

next_id = 3

# ----------------------------------------------------------------------
# المسارات
# ----------------------------------------------------------------------

@app.route('/')
@app.route('/products')
def product_list():
    return render_template(
        'index.jinja',
        products=PRODUCTS_BASE_DATA
    )


@app.route('/products/<int:product_id>')
def product_detail(product_id):
    product = None

    for p in PRODUCTS_BASE_DATA:
        if p['id'] == product_id:
            product = p
            break

    if product is None:
        return "Product Not Found"

    return render_template(
        'product_detail.jinja',
        product=product
    )


@app.route('/add', methods=['GET', 'POST'])
def add_product():
    global next_id

    if request.method == 'POST':
        image_filename = 'default.png'

        if 'image_file' in request.files:
            file = request.files['image_file']
            if file:
                filename = secure_filename(file.filename)
                file.save(os.path.join(UPLOAD_FOLDER, filename))
                image_filename = filename

        new_product = {
            'id': next_id,
            'name': request.form['name'],
            'price': float(request.form['price']),
            'details': request.form['details'],
            'image_filename': image_filename
        }

        PRODUCTS_BASE_DATA.append(new_product)
        next_id += 1

        return redirect(url_for('product_list'))

    return render_template('add_product.jinja')


@app.route('/delete/<int:product_id>', methods=['POST'])
def delete_product(product_id):
    global PRODUCTS_BASE_DATA

    product_to_delete = None

    for p in PRODUCTS_BASE_DATA:
        if p['id'] == product_id:
            product_to_delete = p
            break

    if product_to_delete:
        filename_to_delete = product_to_delete.get('image_filename')

        if filename_to_delete and filename_to_delete != 'default.png':
            file_path = os.path.join(
                UPLOAD_FOLDER,
                filename_to_delete
            )

            if os.path.exists(file_path):
                os.remove(file_path)

        new_products = []
        for p in PRODUCTS_BASE_DATA:
            if p['id'] != product_id:
                new_products.append(p)

        PRODUCTS_BASE_DATA = new_products

    return redirect(url_for('product_list'))


if __name__ == '__main__':
    app.run()
