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
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.Generator;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;
import symbolTableJinja.JinjaSymbolTable;
import symbolTableJinja.JinjaAstSymbolTableBuilder;

import java.nio.file.Path;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        printBanner("Flask App Compiler — Full Pipeline");

        // ── 1. Python pipeline (valid Flask app) ─────────────────────────────
        PythonNode pythonAst = runPythonPipeline("flask-app/app.py");

        // ── 2. Generator: extract render_template() context variables ─────────
        Generator generator = new Generator();
        
        CharStream input = CharStreams.fromFileName("flask-app/app.py");
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        FlaskPythonParser.ProgramContext pythonParseTree = parser.program();
        
        if (pythonParseTree != null) {
            generator.visit(pythonParseTree);
        }
        generator.printReport();
        Map<String, Set<String>> templateContextVars = generator.getTemplateContextVars();

        // ── 2.5 Mock Data Extractor, then bind Jinja names to those literals ─
        semantic.MockDataExtractor dataExtractor = new semantic.MockDataExtractor();
        if (pythonAst != null) {
            pythonAst.accept(dataExtractor);
        }
        dataExtractor.printReport();
        Map<String, Object> mockData = generator.bind(dataExtractor.getExtractedData());

        // ── 3. Template pipeline (valid Flask templates) ──────────────────────
        for (String template : List.of(
                "flask-app/templates/base.jinja",
                "flask-app/templates/index.jinja",
                "flask-app/templates/product_detail.jinja",
                "flask-app/templates/add_product.jinja"
        )) {
            String tmplName = Path.of(template).getFileName().toString();
            Set<String> ctxVars = contextFor(tmplName, templateContextVars);
            runTemplatePipeline(template, ctxVars, mockData);
        }

        // ── 4. Semantic error demo: Python ────────────────────────────────────
        printBanner("SEMANTIC ERROR DEMO — Python (15 checks)");
        runPythonErrorDemo("test/python/error_demo.txt");

        // ── 5. Semantic error demo: Jinja2 ───────────────────────────────────
        printBanner("SEMANTIC ERROR DEMO — Jinja2 (11 checks, AST)");
        Set<String> demoCtxVars = new LinkedHashSet<>(List.of("products", "title"));
        runJinjaErrorDemo("test/jinja/error_demo.jinja", demoCtxVars, mockData);
    }

    /**
     * A parent layout never appears in a render_template() call: it is rendered
     * through whichever child extends it, and sees that child's context. So its
     * context is the union of every child's — otherwise every variable a layout
     * uses would look unsupplied.
     */
    private static Set<String> contextFor(String templateName,
                                          Map<String, Set<String>> contextVars)
            throws Exception {
        Set<String> vars = new LinkedHashSet<>(
                contextVars.getOrDefault(templateName, Set.of()));

        java.io.File dir = new java.io.File("flask-app/templates");
        java.io.File[] siblings = dir.listFiles((d, n) -> n.endsWith(".jinja"));
        if (siblings == null) return vars;

        for (java.io.File child : siblings) {
            String body = java.nio.file.Files.readString(child.toPath());
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\{%\\s*extends\\s*[\"']([^\"']+)[\"']")
                    .matcher(body);
            if (m.find() && m.group(1).equals(templateName)) {
                vars.addAll(contextVars.getOrDefault(child.getName(), Set.of()));
            }
        }
        return vars;
    }

    // ─── Python Pipeline (full) ────────────────────────────────────────────
    private static PythonNode runPythonPipeline(String filePath)
            throws Exception {
        printSection("PYTHON: " + filePath);

        CharStream input = CharStreams.fromFileName(filePath);
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
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

        return ast;
    }

    // ─── Template Pipeline (full) ──────────────────────────────────────────
    private static void runTemplatePipeline(String filePath, Set<String> pythonCtxVars, Map<String, Object> mockData)
            throws Exception {
        printSection("TEMPLATE: " + filePath);

        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
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

        JinjaAstSymbolTableBuilder symbolBuilder =
                new JinjaAstSymbolTableBuilder(templateName, pythonCtxVars);
        rootNode.accept(symbolBuilder);

        JinjaSymbolTable symbolTable = symbolBuilder.getSymbolTable();
        symbolTable.analyze();

        printSub("Jinja2 Symbol Table + Analysis");
        symbolTable.printSymbolTable();
        symbolTable.printAnalysisReport();

        printSub("Template AST");
        PrintASTVisitor.printNode(rootNode, 0);

        // Semantic analysis on the template
        JinjaAstSemanticAnalyzer jinjaAnalyzer =
                new JinjaAstSemanticAnalyzer(templateName, templateDir, pythonCtxVars, mockData);
        rootNode.accept(jinjaAnalyzer);
        jinjaAnalyzer.printReport();
    }

    // ─── Python Error Demo ─────────────────────────────────────────────────
    private static void runPythonErrorDemo(String filePath) throws Exception {
        printSection("PYTHON ERROR DEMO: " + filePath);

        CharStream input = CharStreams.fromFileName(filePath);
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
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
    private static void runJinjaErrorDemo(String filePath, Set<String> pythonCtxVars, Map<String, Object> mockData)
            throws Exception {
        printSection("JINJA2 ERROR DEMO: " + filePath);

        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
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

        TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot(tree);
        JinjaAstSemanticAnalyzer analyzer =
                new JinjaAstSemanticAnalyzer(templateName, templateDir, pythonCtxVars, mockData);
        rootNode.accept(analyzer);
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
