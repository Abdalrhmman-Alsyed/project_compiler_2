package tests.test8_codegen_index;

import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import codeGenerator.HtmlCodeGenerator;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import semantic.Generator;
import symbolTable.PythonSymbolTable;
import symbolTable.visitores.PythonSymbolTableBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RunTest8 {
    public static void main(String[] args) throws Exception {
        String appPy = "tests/test8_codegen_index/app.py";
        String templatesDir = "tests/test8_codegen_index/templates";
        String outputDir = "compiler_output/test8_output";

        // 1. بناء شجرة بايثون (AST)
        CharStream input = CharStreams.fromFileName(appPy);
        FlaskPythonLexer lexer = new FlaskPythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskPythonParser parser = new FlaskPythonParser(tokens);
        parser.removeErrorListeners();
        PythonNode ast = new PythonASTBuilderVisitor().visit(parser.program());

        // 2. استخراج الـ Context
        Generator generator = new Generator();
        CharStream inputGen = CharStreams.fromFileName(appPy);
        FlaskPythonLexer lexerGen = new FlaskPythonLexer(inputGen);
        FlaskPythonParser parserGen = new FlaskPythonParser(new CommonTokenStream(lexerGen));
        generator.visit(parserGen.program());

        // 3. استخراج البيانات الوهمية (Mock Data)
        semantic.MockDataExtractor dataExtractor = new semantic.MockDataExtractor();
        ast.accept(dataExtractor);
        java.util.Map<String, Object> mockData = generator.bind(dataExtractor.getExtractedData());

        // 3. تشغيل المولد وإنتاج الـ HTML النهائي
        HtmlCodeGenerator htmlGen = new HtmlCodeGenerator(Paths.get(templatesDir), Paths.get(outputDir));
        htmlGen.loadTemplates();
        
        System.out.println("=== توليد الكود (Code Generation) ===");
        System.out.println("Extracted Data: " + dataExtractor.getExtractedData());
System.out.println("Mock Data: " + mockData);
        List<Path> writtenFiles = htmlGen.generateRenderedPages(generator, mockData, "Test8 Codegen Check", true);

        System.out.println("\n=== محتوى ملف HTML المولد ===");
        for (Path p : writtenFiles) {
            System.out.println("الملف: " + p.getFileName());
            System.out.println("-".repeat(40));
            System.out.println(Files.readString(p));
        }
    }
}
