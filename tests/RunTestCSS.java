import codeGenerator.HtmlCodeGenerator;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.Generator;
import semantic.JinjaAstSemanticAnalyzer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RunTestCSS {
    public static void main(String[] args) throws Exception {
        System.out.println("\n############################################################");
        System.out.println("   Test CSS code generation");
        System.out.println("############################################################\n");

        String pythonFile = "tests/test_css/app.py";
        System.out.println("-> Parsing Python: " + pythonFile);

        FlaskPythonLexer lexer = new FlaskPythonLexer(CharStreams.fromPath(Path.of(pythonFile)));
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(lexer));
        FlaskPythonParser.ProgramContext tree = parser.program();
        ast.python.PythonNode ast = new ast.python.visitors.PythonASTBuilderVisitor().visit(tree);

        Generator generator = new Generator();
        ast.accept(generator);

        System.out.println("-> Semantic Analysis OK. Templates to generate: " + generator.getTemplateContextVars().keySet());

        HtmlCodeGenerator htmlGen = new HtmlCodeGenerator(
                Path.of("tests/test_css/templates"),
                Path.of("tests/test_css/output"));
        htmlGen.loadTemplates();
        
        System.out.println("-> Jinja Semantic Analysis");

        htmlGen.generateRenderedPages(generator, new HashMap<>(), "Testing CSS", true);
        System.out.println("Done! Check tests/test_css/output");
    }
}
