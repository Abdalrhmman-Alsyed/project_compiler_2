import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class TestSingle {
    public static void main(String[] args) throws Exception {
        CharStream charStream = CharStreams.fromFileName("flask-app/templates/add_product.jinja", StandardCharsets.UTF_8);
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // Print tokens
        tokens.fill();
        for (Token t : tokens.getTokens()) {
            System.out.println("Token: " + t.getType() + " text: '" + t.getText().replace("\n", "\\n") + "'");
        }
        
        FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
        parser.template();
        System.out.println("Parse completed.");
    }
}
