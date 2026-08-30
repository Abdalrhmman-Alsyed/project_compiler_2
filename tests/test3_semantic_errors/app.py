from flask import Flask, render_template

app = Flask(__name__)

@app.route("/")
def bad_route():
    # Semantic Error: variable used before assignment
    print(x)
    x = 10
    
    # Semantic Warning: Unused variable
    unused_var = 50
    
    # Semantic Error: break outside loop
    break
    
    return render_template("bad.jinja")

# Semantic Error: return outside function
return 5
