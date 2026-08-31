#!/bin/bash

# Script to compile and run the Java Compiler

echo "⚙️  Compiling Java files..."
# Find all Java files (excluding the code generator if any) and compile them
find src -name "*.java" | grep -v "code generator" > sources.txt
javac -encoding UTF-8 -d out -cp "libs/antlr-4.13.2-complete.jar" @sources.txt

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo "🚀 Running the Compiler..."
    echo "--------------------------------------------------------"
    
    # Run the main class
    java -Dfile.encoding=UTF-8 -cp "out:libs/antlr-4.13.2-complete.jar" Main
    
    echo "--------------------------------------------------------"
    echo "✨ Finished! Check the 'compiler_output' folder for the generated JSON and txt reports."
else
    echo "❌ Compilation failed! Please check the errors above."
fi
