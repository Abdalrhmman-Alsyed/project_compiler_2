import gen.FlaskJinjaLexer;
import org.antlr.v4.runtime.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class PrintTokens {
    public static void main(String[] args) throws Exception {
        CharStream charStream = CharStreams.fromFileName("flask-app/templates/index.jinja", StandardCharsets.UTF_8);
        FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
        for (Token token : lexer.getAllTokens()) {
            System.out.println("Token: " + lexer.getVocabulary().getSymbolicName(token.getType()) + " text: '" + token.getText().replace("\n", "\\n") + "'");
        }
    }
}
