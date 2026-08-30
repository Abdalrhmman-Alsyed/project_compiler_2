import ast.template.TemplateNode;
import ast.visitors.PrintASTVisitor;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import semantic.JinjaAstSemanticAnalyzer;
import symbolTableJinja.JinjaAstSymbolTableBuilder;
import symbolTableJinja.JinjaSymbolTable;

import java.nio.file.Path;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

public class TestJinja {
    public static void main(String[] args) throws Exception {
        String filePath = "jinja_tests/test_jinja_nesting.html";
        Set<String> pythonCtxVars = new LinkedHashSet<>(List.of("products", "page_title"));

        CharStream charStream = CharStreams.fromPath(Path.of(filePath));
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
        FlaskTemplateParser.TemplateRootContext tree = (FlaskTemplateParser.TemplateRootContext) parser.template();
        
        TemplateNode rootNode = new TemplateASTBuilder().visitTemplateRoot(tree);

        String templateName = Path.of(filePath).getFileName().toString();
        String templateDir  = Path.of(filePath).getParent().toString();

        System.out.println("=== Template AST ===");
        PrintASTVisitor.printNode(rootNode, 0);

        System.out.println("\n=== Jinja2 Symbol Table + Analysis ===");
        JinjaAstSymbolTableBuilder symbolBuilder = new JinjaAstSymbolTableBuilder(templateName, pythonCtxVars);
        rootNode.accept(symbolBuilder);
        JinjaSymbolTable symbolTable = symbolBuilder.getSymbolTable();
        symbolTable.analyze();
        symbolTable.printSymbolTable();
        symbolTable.printAnalysisReport();

        System.out.println("\n=== Jinja2 Semantic Analysis ===");
        JinjaAstSemanticAnalyzer jinjaAnalyzer = new JinjaAstSemanticAnalyzer(templateName, templateDir, pythonCtxVars);
        rootNode.accept(jinjaAnalyzer);
        jinjaAnalyzer.printReport();
    }
}
