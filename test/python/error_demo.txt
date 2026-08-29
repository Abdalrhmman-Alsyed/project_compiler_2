def calculate_total(price, discount, price): # ERROR: duplicate parameter 'price'
    total = price - discount
    return total
    print("This will never print") # ERROR: unreachable code

def calculate_total(amount): # ERROR: duplicate function
    break # ERROR: break outside loop
    return amount

def main():
    x = 10
    if x > 5:
        y = 20
    
    # y is only defined in the if-block. In some languages this is an error.
    # But in Python it's allowed unless the analyzer is strict.
    # Let's test a variable that is definitely not defined.
    print(z) # ERROR: variable 'z' is not defined

    # Let's test calling a variable from another scope
    print(total) # ERROR: variable 'total' is out of scope (defined in calculate_total)

    global_test = 50
    global global_test # ERROR: assigned locally before global declaration

main()
return # ERROR: return outside function
