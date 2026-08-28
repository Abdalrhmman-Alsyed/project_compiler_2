import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import symbolTableJinja.JinjaSymbolTable;
import symbolTableJinja.JinjaAstSymbolTableBuilder;

import java.nio.file.Path;

import static ast.visitors.PrintASTVisitor.printNode;

public class TemplateASTTest {

    public static void main(String[] args) throws Exception {
        Path templatePath1 = Path.of("test/jinja/base.html");
        Path templatePath2 = Path.of("test/jinja/index.html");
        Path templatePath3 = Path.of("test/jinja/product_detail.html");
        Path templatePath4 = Path.of("test/jinja/add_product.html");

        // 1. إعداد Lexer
        CharStream charStream = CharStreams.fromPath(templatePath2);
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // 2. إعداد Parser
        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);

        // 3. إنشاء شجرة Parse
        FlaskTemplateParser.TemplateRootContext tree = (FlaskTemplateParser.TemplateRootContext) parser.template();

        // 4. زيارة الشجرة باستخدام TemplateASTBuilder
        TemplateASTBuilder visitor = new TemplateASTBuilder();
        TemplateNode rootNode = visitor.visitTemplateRoot(tree);

        // 2. بناء جدول الرموز
        JinjaAstSymbolTableBuilder builder = new JinjaAstSymbolTableBuilder("test.html", java.util.Set.of());
        rootNode.accept(builder);

        // 3. التحليل
        JinjaSymbolTable symbolTable = builder.getSymbolTable();
        symbolTable.analyze();

        // 4. عرض النتائج
        symbolTable.printSymbolTable();
        symbolTable.printAnalysisReport();

        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();

        // 5. طباعة النتيجة
        printNode(rootNode, 0);
    }

}
