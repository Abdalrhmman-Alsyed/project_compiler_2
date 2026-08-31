#!/bin/bash

# Script to start the Flask Web Server

# 1. Activate the Python virtual environment
source venv/bin/activate

# 2. Navigate to the flask app directory
cd flask-app

# 3. Inform the user and start the app
echo "🚀 Starting Flask Server on http://127.0.0.1:5000"
python app.py
