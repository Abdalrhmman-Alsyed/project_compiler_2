import codeGenerator.HtmlCodeGenerator;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.Generator;

import java.nio.file.Path;
import java.util.HashMap;

public class RunTestComprehensive {
    public static void main(String[] args) throws Exception {
        System.out.println("\n############################################################");
        System.out.println("   Comprehensive HTML Generation Test");
        System.out.println("############################################################\n");

        String pythonFile = "tests/test_all_features/app.py";
        System.out.println("-> Parsing Python: " + pythonFile);

        FlaskPythonLexer lexer = new FlaskPythonLexer(CharStreams.fromPath(Path.of(pythonFile)));
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(lexer));
        FlaskPythonParser.ProgramContext tree = parser.program();
        ast.python.PythonNode ast = new ast.python.visitors.PythonASTBuilderVisitor().visit(tree);

        Generator generator = new Generator();
        ast.accept(generator);

        System.out.println("-> Semantic Analysis OK. Templates to generate: " + generator.getTemplateContextVars().keySet());

        HtmlCodeGenerator htmlGen = new HtmlCodeGenerator(
                Path.of("tests/test_all_features/templates"),
                Path.of("tests/test_all_features/output"));
        htmlGen.loadTemplates();
        
        System.out.println("-> Generating HTML");
        
        // Mock data injection
        java.util.Map<String, Object> bound = new java.util.HashMap<>();
        bound.put("title", "Comprehensive Test with Data");
        
        java.util.List<java.util.Map<String, Object>> users = new java.util.ArrayList<>();
        users.add(java.util.Map.of("name", "Alice", "role", "Admin", "active", true));
        users.add(java.util.Map.of("name", "Bob", "role", "Editor", "active", false));
        users.add(java.util.Map.of("name", "Charlie", "role", "Viewer", "active", true));
        bound.put("users", users);

        htmlGen.generateRenderedPages(generator, bound, "Comprehensive Testing", true);
        System.out.println("Done! Check tests/test_all_features/output");
    }
}
