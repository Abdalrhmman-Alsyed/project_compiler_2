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
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.Generator;
import codeGenerator.HtmlCodeGenerator;
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
import ast.util.ASTJsonSerializer;

public class Main {

    public static void main(String[] args) throws Exception {
        printBanner("Flask App Compiler — Full Pipeline");

        // Create compiler_output directory and clear semantic report
        Path compilerOutput = Paths.get("compiler_output");
        Files.createDirectories(compilerOutput);
        Path semanticReport = compilerOutput.resolve("semantic_report.txt");
        Files.deleteIfExists(semanticReport);

        // ── 1. Python pipeline (valid Flask app) ─────────────────────────────
        PythonNode pythonAst = runPythonPipeline("flask-app/app.py");

        // Write Python AST to JSON
        String pythonJson = ASTJsonSerializer.toJson(pythonAst);
        Files.writeString(compilerOutput.resolve("ast_python.json"), pythonJson);

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
        Map<String, TemplateNode> jinjaAstMap = new LinkedHashMap<>();
        for (String template : List.of(
                "flask-app/templates/base.jinja",
                "flask-app/templates/index.jinja",
                "flask-app/templates/product_detail.jinja",
                "flask-app/templates/add_product.jinja"
        )) {
            String tmplName = Path.of(template).getFileName().toString();
            Set<String> ctxVars = contextFor(tmplName, templateContextVars);
            TemplateNode tAst = runTemplatePipeline(template, ctxVars, mockData);
            jinjaAstMap.put(tmplName, tAst);
        }

        // Write Jinja ASTs to JSON
        String jinjaJson = ASTJsonSerializer.toJson(jinjaAstMap);
        Files.writeString(compilerOutput.resolve("ast_jinja.json"), jinjaJson);

        // ── 3.5 HTML code generation ─────────────────────────────────────────
        printBanner("HTML CODE GENERATION -> output/");
        HtmlCodeGenerator htmlGen = new HtmlCodeGenerator(
                Path.of("flask-app/templates"),
                Path.of("output"));
        htmlGen.loadTemplates();
        htmlGen.generateRenderedPages(generator, mockData,
                "compiler pipeline (Main) -- first create of HTML pages", true);
        htmlGen.printReport();

        // ── 4. Serve output/ immediately so the browser can connect ──────────
        int port = 8080;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        printBanner("STORE SERVER -> http://localhost:" + port + "/");
        OutputHttpServer store = new OutputHttpServer(port);
        store.startServing(generator, htmlGen, mockData);


        System.out.println("\nStore is running at http://localhost:" + port + "/");
        System.out.println("Stop with Ctrl+C");
        store.awaitStop();
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
        PythonSemanticAnalyzer semanticAnalyzer = new PythonSemanticAnalyzer(symbolTable);
        ast.accept(semanticAnalyzer);
        semanticAnalyzer.printReport();
        semanticAnalyzer.saveReportToFile("compiler_output/semantic_report.txt");


        return ast;
    }

    // ─── Template Pipeline (full) ──────────────────────────────────────────
    private static TemplateNode runTemplatePipeline(String filePath, Set<String> pythonCtxVars, Map<String, Object> mockData)
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

        JinjaSymbolTableBuilder symbolBuilder = new JinjaSymbolTableBuilder();
        rootNode.accept(symbolBuilder);

        JinjaSymbolTable symbolTable = symbolBuilder.getSymbolTable();

        printSub("Jinja2 Symbol Table");
        symbolTable.print();

        printSub("Template AST");
        PrintASTVisitor.printNode(rootNode, 0);

        // Semantic analysis on the template
        semantic.JinjaAstSemanticAnalyzer jinjaAnalyzer =
                new semantic.JinjaAstSemanticAnalyzer(templateName, templateDir, pythonCtxVars, mockData, symbolTable);
        rootNode.accept(jinjaAnalyzer);
        jinjaAnalyzer.printReport();
        jinjaAnalyzer.saveReportToFile("compiler_output/semantic_report.txt");

        return rootNode;
    }


    // ─── Formatting Helpers ────────────────────────────────────────────────
    private static void printBanner(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    private static void printSection(String title) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("  " + title);
        System.out.println("-".repeat(70));
    }

    private static void printSub(String label) {
        System.out.println("\n--- " + label + " ---");
    }
}
