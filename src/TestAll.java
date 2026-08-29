import gen.FlaskJinjaLexer;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import java.io.File;

public class TestAll {
    public static void main(String[] args) throws Exception {
        File dir = new File("flask-app/templates");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jinja") && !name.equals("compiler.jinja"));
        if (files == null) return;
        boolean allPassed = true;
        for (File f : files) {
            System.out.println("Testing " + f.getName() + "...");
            CharStream charStream = CharStreams.fromPath(f.toPath());
            FlaskJinjaLexer lexer = new FlaskJinjaLexer(charStream);
            
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> r, Object off, int line, int col, String msg, RecognitionException e) {
                    System.err.println("Lexer Error in " + f.getName() + " at " + line + ":" + col + " - " + msg);
                }
            });
            
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
            
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> r, Object off, int line, int col, String msg, RecognitionException e) {
                    System.err.println("Parser Error in " + f.getName() + " at " + line + ":" + col + " - " + msg);
                }
            });
            
            parser.template();
        }
        System.out.println("Done testing all files.");
    }
}
