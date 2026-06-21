import ast.python.PythonNode;
import ast.python.visitors.PythonASTPrinter;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.PrintASTVisitor;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskLexer;
import gen.FlaskPythonParser;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.Generator;
import semantic.JinjaSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;
import symbolTableJinja.JinjaSymbolTable;
import symbolTableJinja.JinjaSymbolTableBuilder;

import java.nio.file.Path;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        printBanner("Flask App Compiler — Full Pipeline");

        // ── 1. Python pipeline (valid Flask app) ─────────────────────────────
        FlaskPythonParser.ProgramContext pythonParseTree =
                runPythonPipeline("test/python/python_test.txt");

        // ── 2. Generator: extract render_template() context variables ─────────
        Generator generator = new Generator();
        if (pythonParseTree != null) {
            generator.visit(pythonParseTree);
        }
        generator.printReport();

        Map<String, Set<String>> templateContextVars = generator.getTemplateContextVars();

        // ── 3. Template pipeline (valid Flask templates) ──────────────────────
        for (String template : List.of(
                "test/jinja/base.html",
                "test/jinja/index.html",
                "test/jinja/product_detail.html",
                "test/jinja/add_product.html"
        )) {
            String tmplName = Path.of(template).getFileName().toString();
            Set<String> ctxVars = templateContextVars.getOrDefault(tmplName, Set.of());
            runTemplatePipeline(template, ctxVars);
        }

        // ── 4. Semantic error demo: Python ────────────────────────────────────
        printBanner("SEMANTIC ERROR DEMO — Python (15 checks)");
        runPythonErrorDemo("test/python/error_demo.txt");

        // ── 5. Semantic error demo: Jinja2 ───────────────────────────────────
        printBanner("SEMANTIC ERROR DEMO — Jinja2 (10 checks)");
        Set<String> demoCtxVars = new LinkedHashSet<>(List.of("products", "title"));
        runJinjaErrorDemo("test/jinja/error_demo.html", demoCtxVars);
    }

    // ─── Python Pipeline (full) ────────────────────────────────────────────
    private static FlaskPythonParser.ProgramContext runPythonPipeline(String filePath)
            throws Exception {
        printSection("PYTHON: " + filePath);

        CharStream input = CharStreams.fromFileName(filePath);
        FlaskLexer lexer = new FlaskLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym,
                                    int line, int col, String msg, RecognitionException e) {
                System.err.println("[Python Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        FlaskPythonParser.ProgramContext parseTree =
                (FlaskPythonParser.ProgramContext) parser.program();

        PythonASTBuilderVisitor astBuilder = new PythonASTBuilderVisitor();
        PythonNode ast = astBuilder.visit(parseTree);

        printSub("AST");
        ast.accept(new PythonASTPrinter());

        PythonSymbolTable symbolTable = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(symbolTable));

        printSub("Symbol Table");
        symbolTable.print();

        // Semantic analysis on the valid Flask app
        PythonSemanticAnalyzer semanticAnalyzer = new PythonSemanticAnalyzer();
        ast.accept(semanticAnalyzer);
        semanticAnalyzer.printReport();

        return parseTree;
    }

    // ─── Template Pipeline (full) ──────────────────────────────────────────
    private static void runTemplatePipeline(String filePath, Set<String> pythonCtxVars)
            throws Exception {
        printSection("TEMPLATE: " + filePath);

        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskLexer lexer = new FlaskLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym,
                                    int line, int col, String msg, RecognitionException e) {
                System.err.println("[Template Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        FlaskTemplateParser.TemplateRootContext tree =
                (FlaskTemplateParser.TemplateRootContext) parser.template();

        TemplateASTBuilder astBuilder = new TemplateASTBuilder();
        TemplateNode rootNode = astBuilder.visitTemplateRoot(tree);

        String templateName = Path.of(filePath).getFileName().toString();
        String templateDir  = Path.of(filePath).getParent().toString();

        JinjaSymbolTableBuilder symbolBuilder = new JinjaSymbolTableBuilder(templateName);
        symbolBuilder.visit(tree);

        JinjaSymbolTable symbolTable = symbolBuilder.getSymbolTable();
        symbolTable.analyze();

        printSub("Jinja2 Symbol Table + Analysis");
        symbolTable.printSymbolTable();
        symbolTable.printAnalysisReport();

        printSub("Template AST");
        PrintASTVisitor.printNode(rootNode, 0);

        // Semantic analysis on the template
        JinjaSemanticAnalyzer jinjaAnalyzer =
                new JinjaSemanticAnalyzer(templateName, templateDir, pythonCtxVars);
        jinjaAnalyzer.visit(tree);
        jinjaAnalyzer.printReport();
    }

    // ─── Python Error Demo ─────────────────────────────────────────────────
    private static void runPythonErrorDemo(String filePath) throws Exception {
        printSection("PYTHON ERROR DEMO: " + filePath);

        CharStream input = CharStreams.fromFileName(filePath);
        FlaskLexer lexer = new FlaskLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym,
                                    int line, int col, String msg, RecognitionException e) {
                System.err.println("[Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        ParseTree parseTree = parser.program();

        PythonASTBuilderVisitor astBuilder = new PythonASTBuilderVisitor();
        PythonNode ast = astBuilder.visit(parseTree);

        PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer();
        ast.accept(analyzer);
        analyzer.printReport();
    }

    // ─── Jinja2 Error Demo ─────────────────────────────────────────────────
    private static void runJinjaErrorDemo(String filePath, Set<String> pythonCtxVars)
            throws Exception {
        printSection("JINJA2 ERROR DEMO: " + filePath);

        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskLexer lexer = new FlaskLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym,
                                    int line, int col, String msg, RecognitionException e) {
                System.err.println("[Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        FlaskTemplateParser.TemplateRootContext tree =
                (FlaskTemplateParser.TemplateRootContext) parser.template();

        String templateName = Path.of(filePath).getFileName().toString();
        String templateDir  = Path.of(filePath).getParent().toString();

        JinjaSemanticAnalyzer analyzer =
                new JinjaSemanticAnalyzer(templateName, templateDir, pythonCtxVars);
        analyzer.visit(tree);
        analyzer.printReport();
    }

    // ─── Formatting Helpers ────────────────────────────────────────────────
    private static void printBanner(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    private static void printSection(String title) {
        System.out.println("\n" + "─".repeat(70));
        System.out.println("  " + title);
        System.out.println("─".repeat(70));
    }

    private static void printSub(String label) {
        System.out.println("\n--- " + label + " ---");
    }
}
