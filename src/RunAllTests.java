import server.OutputHttpServer;
import ast.python.PythonNode;
import ast.python.visitors.PythonASTPrinter;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.PrintASTVisitor;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import semantic.Generator;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;
import symbolTable.JinjaSymbolTable;
import symbolTable.visitores.JinjaSymbolTableBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.File;

public class RunAllTests {

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("            COMPILER TEST SUITE RUNNER                      ");
        System.out.println("============================================================\n");
        
        runTest("tests/test1_basic/app.py", "tests/test1_basic/templates", "Test 1: Basic App", "tests/test1_basic/semantic_log.txt");
        runTest("tests/test2_advanced/app.py", "tests/test2_advanced/templates", "Test 2: Advanced (Loops, Ifs, Includes)", "tests/test2_advanced/semantic_log.txt");
        
        System.out.println("\n\n============================================================");
        System.out.println("            TEST 3: SEMANTIC ERRORS DEMO                    ");
        System.out.println("============================================================\n");
        Files.deleteIfExists(Paths.get("tests/test3_semantic_errors/semantic_log.txt"));
        runPythonErrorDemo("tests/test3_semantic_errors/app.py", "tests/test3_semantic_errors/semantic_log.txt");
        Set<String> demoCtxVars = new LinkedHashSet<>(List.of("items", "title", "user"));
        runJinjaErrorDemo("tests/test3_semantic_errors/templates/bad.jinja", demoCtxVars, new HashMap<>(), "tests/test3_semantic_errors/semantic_log.txt");

        System.out.println("\n\n============================================================");
        System.out.println("            TEST 4: ADVANCED SEMANTIC ERRORS (ALL IN ONE)   ");
        System.out.println("============================================================\n");
        runTest("tests/test4_advanced_semantic_errors/app.py", "tests/test4_advanced_semantic_errors/templates", "Test 4: Ultimate Semantic Errors", "tests/test4_advanced_semantic_errors/semantic_log.txt");
        
        System.out.println("\nAll tests executed. Please review the console output and the 'semantic_log.txt' generated inside each test folder.");
    }

    public static void runTest(String pyFile, String templatesDir, String testName, String logFile) throws Exception {
        System.out.println("\n\n############################################################");
        System.out.println("   " + testName);
        System.out.println("############################################################\n");
        
        Files.deleteIfExists(Paths.get(logFile));
        
        System.out.println("-> Parsing Python: " + pyFile);
        CharStream input = CharStreams.fromFileName(pyFile);
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        
        FlaskPythonParser.ProgramContext parseTree = parser.program();
        PythonASTBuilderVisitor astBuilder = new PythonASTBuilderVisitor();
        PythonNode ast = astBuilder.visit(parseTree);
        
        PythonSymbolTable pySymbolTable = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(pySymbolTable));
        
        PythonSemanticAnalyzer semanticAnalyzer = new PythonSemanticAnalyzer(pySymbolTable);
        ast.accept(semanticAnalyzer);
        System.out.println("\n--- Python Semantic Analysis ---");
        semanticAnalyzer.printReport();
        semanticAnalyzer.saveReportToFile(logFile);

        Generator generator = new Generator();
        ast.accept(generator);
        Map<String, Set<String>> templateContextVars = generator.getTemplateContextVars();
        
        semantic.MockDataExtractor dataExtractor = new semantic.MockDataExtractor();
        ast.accept(dataExtractor);
        Map<String, Object> mockData = generator.bind(dataExtractor.getExtractedData());
        
        File tDir = new File(templatesDir);
        if (tDir.exists() && tDir.isDirectory()) {
            File[] templates = tDir.listFiles((dir, name) -> name.endsWith(".jinja"));
            if (templates != null) {
                for (File t : templates) {
                    System.out.println("\n-> Parsing Jinja Template: " + t.getName());
                    CharStream tInput = CharStreams.fromFileName(t.getAbsolutePath());
                    FlaskJinjaLexer tLexer = new FlaskJinjaLexer(tInput);
                    FlaskTemplateParser tParser = new FlaskTemplateParser(new CommonTokenStream(tLexer));
                    tParser.removeErrorListeners();
                    
                    TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot((gen.FlaskTemplateParser.TemplateRootContext) tParser.template());
                    String tName = t.getName();
                    
                    JinjaSymbolTableBuilder jBuilder = new JinjaSymbolTableBuilder();
                    rootNode.accept(jBuilder);
                    JinjaSymbolTable jSymbolTable = jBuilder.getSymbolTable();
                    
                    Set<String> ctxVars = new LinkedHashSet<>(templateContextVars.getOrDefault(tName, Set.of()));
                    JinjaAstSemanticAnalyzer jAnalyzer = new JinjaAstSemanticAnalyzer(tName, tDir.getAbsolutePath(), ctxVars, mockData, jSymbolTable);
                    rootNode.accept(jAnalyzer);
                    System.out.println("\n--- Jinja Semantic Analysis (" + tName + ") ---");
                    jAnalyzer.printReport();
                    jAnalyzer.saveReportToFile(logFile);
                }
            }
        }
    }
    
    // ─── Python Error Demo ─────────────────────────────────────────────────
    public static void runPythonErrorDemo(String filePath, String logFile) throws Exception {
        System.out.println("\n--- PYTHON ERROR DEMO: " + filePath + " ---");
        CharStream input = CharStreams.fromFileName(filePath);
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(new FlaskPythonLexer(input)));
        parser.removeErrorListeners();
        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());
        
        PythonSymbolTable pySymbolTable = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(pySymbolTable));
        
        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(pySymbolTable);
        ast.accept(analyzer);
        analyzer.printReport();
        analyzer.saveReportToFile(logFile);
    }

    // ─── Jinja2 Error Demo ─────────────────────────────────────────────────
    public static void runJinjaErrorDemo(String filePath, Set<String> pythonCtxVars, Map<String, Object> mockData, String logFile) throws Exception {
        System.out.println("\n--- JINJA2 ERROR DEMO: " + filePath + " ---");
        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskTemplateParser parser = new FlaskTemplateParser(new CommonTokenStream(new FlaskJinjaLexer(charStream)));
        parser.removeErrorListeners();
        TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot((gen.FlaskTemplateParser.TemplateRootContext) parser.template());
        
        JinjaSymbolTableBuilder jBuilder = new JinjaSymbolTableBuilder();
        rootNode.accept(jBuilder);
        JinjaSymbolTable jSymbolTable = jBuilder.getSymbolTable();
        
        JinjaAstSemanticAnalyzer analyzer = new JinjaAstSemanticAnalyzer(Path.of(filePath).getFileName().toString(), Path.of(filePath).getParent().toString(), pythonCtxVars, mockData, jSymbolTable);
        rootNode.accept(analyzer);
        analyzer.printReport();
        analyzer.saveReportToFile(logFile);
    }
}
