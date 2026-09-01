package tests;

import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.python.visitors.PythonASTPrinter;
import ast.template.TemplateNode;
import ast.util.ASTJsonSerializer;
import ast.visitors.PrintASTVisitor;
import ast.visitors.TemplateASTBuilder;
import codeGenerator.HtmlCodeGenerator;
import gen.FlaskJinjaLexer;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import semantic.Generator;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.MockDataExtractor;
import semantic.PythonSemanticAnalyzer;
import symbolTable.JinjaSymbolTable;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.JinjaSymbolTableBuilder;
import symbolTable.visitores.PythonSymbolTableBuilder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestCompilerOutput {

    // =========================================================================
    // 1. CONFIGURATION (تكوين المسارات للاختبار)
    // =========================================================================
    // غيّر هذه المسارات لتختبر أي مجلد تريده بسهولة
    private static final String PYTHON_FILE = "tests/test8_codegen_index/app.py";
    private static final String TEMPLATES_DIR = "tests/test8_codegen_index/templates";
    private static final String OUTPUT_DIR = "tests/test8_codegen_index/output_test";
    
    public static void main(String[] args) throws Exception {
        printBanner("Compiler E2E Test Runner");
        
        Path outputDir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDir);
        Path semanticReport = outputDir.resolve("semantic_report.txt");
        Files.deleteIfExists(semanticReport);

        // ── 1. Python pipeline ─────────────────────────────
        PythonNode pythonAst = runPythonPipeline(PYTHON_FILE, semanticReport.toString());

        // Write Python AST to JSON
        String pythonJson = ASTJsonSerializer.toJson(pythonAst);
        Files.writeString(outputDir.resolve("ast_python.json"), pythonJson);

        // ── 2. Generator: extract render_template() context variables ─────────
        Generator generator = new Generator();
        if (pythonAst != null) pythonAst.accept(generator);
        generator.printReport();
        Map<String, Set<String>> templateContextVars = generator.getTemplateContextVars();

        // ── 2.5 Mock Data Extractor ─
        MockDataExtractor dataExtractor = new MockDataExtractor();
        if (pythonAst != null) pythonAst.accept(dataExtractor);
        dataExtractor.printReport();
        Map<String, Object> mockData = generator.bind(dataExtractor.getExtractedData());

        // ── 3. Template pipeline (Dynamic Discovery) ──────────────────────
        Map<String, TemplateNode> jinjaAstMap = new LinkedHashMap<>();
        
        // البحث الديناميكي عن جميع قوالب جينجا في المجلد المحدد
        File tDir = new File(TEMPLATES_DIR);
        File[] templates = tDir.listFiles((dir, name) -> name.endsWith(".jinja"));
        
        if (templates != null) {
            for (File t : templates) {
                String templatePath = t.getAbsolutePath();
                String tmplName = t.getName();
                
                Set<String> ctxVars = contextFor(tmplName, templateContextVars, TEMPLATES_DIR);
                TemplateNode tAst = runTemplatePipeline(templatePath, ctxVars, mockData, semanticReport.toString());
                jinjaAstMap.put(tmplName, tAst);
            }
        }

        // Write Jinja ASTs to JSON
        String jinjaJson = ASTJsonSerializer.toJson(jinjaAstMap);
        Files.writeString(outputDir.resolve("ast_jinja.json"), jinjaJson);

        // ── 3.5 HTML code generation ─────────────────────────────────────────
        printBanner("HTML CODE GENERATION -> " + OUTPUT_DIR);
        HtmlCodeGenerator htmlGen = new HtmlCodeGenerator(Path.of(TEMPLATES_DIR), outputDir);
        htmlGen.loadTemplates();
        htmlGen.generateRenderedPages(generator, mockData, "Test Runner Execution", true);
        htmlGen.printReport();
        
        System.out.println("\n✅ Test completed successfully! Outputs are in: " + OUTPUT_DIR);
    }

    private static Set<String> contextFor(String templateName, Map<String, Set<String>> contextVars, String templatesDir) throws Exception {
        Set<String> vars = new LinkedHashSet<>(contextVars.getOrDefault(templateName, Set.of()));
        File dir = new File(templatesDir);
        File[] siblings = dir.listFiles((d, n) -> n.endsWith(".jinja"));
        if (siblings == null) return vars;

        for (File child : siblings) {
            String body = Files.readString(child.toPath());
            Matcher m = Pattern.compile("\\{%\\s*extends\\s*[\"']([^\"']+)[\"']").matcher(body);
            if (m.find() && m.group(1).equals(templateName)) {
                vars.addAll(contextVars.getOrDefault(child.getName(), Set.of()));
            }
        }
        return vars;
    }

    // ─── Python Pipeline (full) ────────────────────────────────────────────
    private static PythonNode runPythonPipeline(String filePath, String reportPath) throws Exception {
        printSection("PYTHON: " + filePath);
        CharStream input = CharStreams.fromFileName(filePath);
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(new FlaskPythonLexer(input)));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym, int line, int col, String msg, RecognitionException e) {
                System.err.println("[Python Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());
        printSub("AST");
        ast.accept(new PythonASTPrinter());

        PythonSymbolTable symbolTable = new PythonSymbolTable();
        ast.accept(new PythonSymbolTableBuilder(symbolTable));
        printSub("Symbol Table");
        symbolTable.print();

        PythonSemanticAnalyzer semanticAnalyzer = new PythonSemanticAnalyzer(symbolTable);
        ast.accept(semanticAnalyzer);
        semanticAnalyzer.printReport();
        semanticAnalyzer.saveReportToFile(reportPath);

        return ast;
    }

    // ─── Template Pipeline (full) ──────────────────────────────────────────
    private static TemplateNode runTemplatePipeline(String filePath, Set<String> pythonCtxVars, Map<String, Object> mockData, String reportPath) throws Exception {
        printSection("TEMPLATE: " + filePath);
        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskTemplateParser parser = new FlaskTemplateParser(new CommonTokenStream(new FlaskJinjaLexer(charStream)));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym, int line, int col, String msg, RecognitionException e) {
                System.err.println("[Template Parse Error] " + line + ":" + col + " " + msg);
            }
        });

        TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot((gen.FlaskTemplateParser.TemplateRootContext) parser.template());
        String templateName = Path.of(filePath).getFileName().toString();
        String templateDir  = Path.of(filePath).getParent().toString();

        JinjaSymbolTableBuilder symbolBuilder = new JinjaSymbolTableBuilder();
        rootNode.accept(symbolBuilder);
        JinjaSymbolTable symbolTable = symbolBuilder.getSymbolTable();
        printSub("Jinja2 Symbol Table");
        symbolTable.print();

        printSub("Template AST");
        PrintASTVisitor.printNode(rootNode, 0);

        JinjaAstSemanticAnalyzer jinjaAnalyzer = new JinjaAstSemanticAnalyzer(templateName, templateDir, pythonCtxVars, mockData, symbolTable);
        rootNode.accept(jinjaAnalyzer);
        jinjaAnalyzer.printReport();
        jinjaAnalyzer.saveReportToFile(reportPath);

        return rootNode;
    }

    private static void printBanner(String title) {
        System.out.println("\n" + "=".repeat(70) + "\n  " + title + "\n" + "=".repeat(70));
    }
    private static void printSection(String title) {
        System.out.println("\n" + "-".repeat(70) + "\n  " + title + "\n" + "-".repeat(70));
    }
    private static void printSub(String label) {
        System.out.println("\n--- " + label + " ---");
    }
}
