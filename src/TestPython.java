import gen.*;
import ast.python.*;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.python.visitors.PythonASTPrinter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import semantic.PythonSemanticAnalyzer;
import semantic.SemanticError;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;

public class TestPython {
    public static void main(String[] args) throws Exception {
        String filepath = "python_tests/test_errors.py";
        CharStream charStream = CharStreams.fromFileName(filepath);
        FlaskPythonLexer lexer = new FlaskPythonLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        
        System.out.println("--- 1. PARSING ---");
        FlaskPythonParser.ProgramContext tree = parser.program();
        System.out.println("Parse successful.");

        System.out.println("\n--- 2. AST GENERATION ---");
        PythonASTBuilderVisitor astBuilder = new PythonASTBuilderVisitor();
        PythonNode ast = astBuilder.visit(tree);
        if (ast == null) {
            System.err.println("AST IS NULL!");
            return;
        }
        System.out.println("AST Generated. Printing AST:");
        ast.accept(new PythonASTPrinter());

        System.out.println("\n--- 3. SYMBOL TABLE ---");
        PythonSymbolTable symbolTable = new PythonSymbolTable();
        PythonSymbolTableBuilder symBuilder = new PythonSymbolTableBuilder(symbolTable);
        ast.accept(symBuilder);
        symbolTable.print();

        System.out.println("\n--- 4. SEMANTIC ANALYSIS ---");
        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(null);
        ast.accept(analyzer);
        analyzer.printReport();
    }
}
