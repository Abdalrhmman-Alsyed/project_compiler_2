import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.MockDataExtractor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class TestMock {

    public static void main(String[] args) throws Exception {
        System.out.println("Running TestMock...");

        // 1. Python Pipeline
        String pythonFile = "test/python/test_mock.py";
        CharStream pyInput = CharStreams.fromFileName(pythonFile);
        FlaskPythonLexer pyLexer = new FlaskPythonLexer(pyInput);
        FlaskPythonParser pyParser = new FlaskPythonParser(new CommonTokenStream(pyLexer));
        PythonNode pyAst = new PythonASTBuilderVisitor().visit(pyParser.program());

        MockDataExtractor dataExtractor = new MockDataExtractor();
        pyAst.accept(dataExtractor);
        dataExtractor.printReport();
        Map<String, Object> mockData = dataExtractor.getExtractedData();

        // 2. Jinja Pipeline
        String jinjaFile = "test/jinja/test_mock.jinja";
        CharStream jinjaInput = CharStreams.fromFileName(jinjaFile);
        FlaskJinjaLexer jinjaLexer = new FlaskJinjaLexer(jinjaInput);
        FlaskTemplateParser jinjaParser = new FlaskTemplateParser(new CommonTokenStream(jinjaLexer));
        
        FlaskTemplateParser.TemplateRootContext tree = (FlaskTemplateParser.TemplateRootContext) jinjaParser.template();
        TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot(tree);

        // 3. Semantic Analysis
        Set<String> ctxVars = Set.of("users"); // Simulating generator extracting the context var
        JinjaAstSemanticAnalyzer jinjaAnalyzer = new JinjaAstSemanticAnalyzer(
                "test_mock.jinja", "test/jinja", ctxVars, mockData);
        rootNode.accept(jinjaAnalyzer);
        jinjaAnalyzer.printReport();
    }
}
