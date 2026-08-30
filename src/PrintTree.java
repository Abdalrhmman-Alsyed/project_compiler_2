import gen.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;

public class PrintTree {
    public static void main(String[] args) throws Exception {
        CharStream charStream = CharStreams.fromFileName("flask-app/templates/index.jinja", StandardCharsets.UTF_8);
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
        ParseTree tree = parser.template();
        System.out.println(tree.toStringTree(parser));
    }
}
