package tests.test5_comprehensive_semantic_errors;

import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import semantic.PythonSemanticAnalyzer;
import semantic.JinjaAstSemanticAnalyzer;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;
import symbolTable.JinjaSymbolTable;
import symbolTable.visitores.JinjaSymbolTableBuilder;
import java.nio.file.Path;
import java.util.*;

public class RunTest5 {
    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("Running Comprehensive Test 5: Python Errors");
        System.out.println("=================================================");
        String pythonFilePath = "tests/test5_comprehensive_semantic_errors/app.py";
        CharStream input = CharStreams.fromFileName(pythonFilePath);
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        parser.removeErrorListeners();
        FlaskPythonParser.ProgramContext parseTree = parser.program();
        
        PythonNode ast = new PythonASTBuilderVisitor().visit(parseTree);
        
        PythonSymbolTable symbolTable = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(symbolTable));

        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(symbolTable);
        ast.accept(analyzer);
        analyzer.printReport();

        System.out.println("\n=================================================");
        System.out.println("Running Comprehensive Test 5: Jinja Errors");
        System.out.println("=================================================");
        String jinjaFilePath = "tests/test5_comprehensive_semantic_errors/jinja_errors.jinja";
        CharStream tInput = CharStreams.fromFileName(jinjaFilePath);
        FlaskJinjaLexer tLexer = new FlaskJinjaLexer(tInput);
        CommonTokenStream tTokens = new CommonTokenStream(tLexer);
        FlaskTemplateParser tParser = new FlaskTemplateParser(tTokens);
        tParser.removeErrorListeners();
        FlaskTemplateParser.TemplateRootContext tParseTree = (FlaskTemplateParser.TemplateRootContext) tParser.template();

        TemplateNode tAst = new TemplateASTBuilder().visit(tParseTree);
        
        JinjaSymbolTableBuilder jBuilder = new JinjaSymbolTableBuilder();
        tAst.accept(jBuilder);
        JinjaSymbolTable jSymbolTable = jBuilder.getSymbolTable();

        Set<String> ctxVars = Set.of("template_var", "items", "mock_dict", "mock_list");
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("mock_dict", new HashMap<String, Object>());
        mockData.put("mock_list", new ArrayList<Object>());

        JinjaAstSemanticAnalyzer jAnalyzer = new JinjaAstSemanticAnalyzer(
            "jinja_errors.jinja",
            Path.of(jinjaFilePath).getParent().toString(),
            ctxVars,
            mockData,
            jSymbolTable
        );
        System.out.println("JINJA AST CHILDREN: " + tAst.getChildren().size()); tAst.accept(jAnalyzer);
        jAnalyzer.printReport();
    }
}
