import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.python.visitors.PythonASTPrinter;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import org.antlr.v4.runtime.*;

public class DumpDataAST {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromFileName("test/python/mock_data.py");
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        FlaskPythonParser.ProgramContext parseTree = parser.program();
        PythonNode ast = new PythonASTBuilderVisitor().visit(parseTree);
        ast.accept(new PythonASTPrinter());
    }
}
