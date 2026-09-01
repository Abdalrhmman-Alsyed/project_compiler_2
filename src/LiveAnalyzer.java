import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTBuilder;
import gen.FlaskJinjaLexer;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import gen.FlaskTemplateParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import semantic.SemanticError;

import java.nio.file.Path;
import java.util.*;

/**
 * Standalone semantic analyzer invoked by Flask for live code analysis.
 * Usage: java LiveAnalyzer <python|jinja> <filepath>
 * Output (stdout): one JSON line  { "errors":[...], "warnings":[...] }
 */
public class LiveAnalyzer {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("{\"errors\":[],\"warnings\":[]}");
            return;
        }

        String lang     = args[0].toLowerCase();
        String filePath = args[1];

        List<SemanticError> errors   = new ArrayList<>();
        List<SemanticError> warnings = new ArrayList<>();
        String parseError = null;

        try {
            if ("python".equals(lang)) {
                CharStream input = CharStreams.fromFileName(filePath);
                FlaskPythonLexer lexer = new FlaskPythonLexer(input);
                lexer.removeErrorListeners();
                CommonTokenStream tokens = new CommonTokenStream(lexer);

                FlaskPythonParser parser = new FlaskPythonParser(tokens);
                parser.removeErrorListeners();

                ParseTree parseTree = parser.program();
                PythonASTBuilderVisitor astBuilder = new PythonASTBuilderVisitor();
                PythonNode ast = astBuilder.visit(parseTree);

                PythonSemanticAnalyzer analyzer = new PythonSemanticAnalyzer(null);
                ast.accept(analyzer);
                errors.addAll(analyzer.getErrors());
                warnings.addAll(analyzer.getWarnings());

            } else if ("jinja".equals(lang)) {
                CharStream input = CharStreams.fromPath(Path.of(filePath));
                FlaskJinjaLexer lexer = new FlaskJinjaLexer(input);
                lexer.removeErrorListeners();
                CommonTokenStream tokens = new CommonTokenStream(lexer);

                FlaskTemplateParser parser = new FlaskTemplateParser(tokens);
                parser.removeErrorListeners();

                FlaskTemplateParser.TemplateRootContext tree =
                        (FlaskTemplateParser.TemplateRootContext) parser.template();

                TemplateNode ast = new TemplateASTBuilder().visitTemplateRoot(tree);

                String templateName = Path.of(filePath).getFileName().toString();
                String templateDir  = Path.of(filePath).getParent() != null
                        ? Path.of(filePath).getParent().toString() : ".";

                JinjaAstSemanticAnalyzer analyzer =
                        new JinjaAstSemanticAnalyzer(templateName, templateDir, Set.of(), null);
                ast.accept(analyzer);
                errors.addAll(analyzer.getErrors());
                warnings.addAll(analyzer.getWarnings());
            }
        } catch (Exception ex) {
            parseError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }

        System.out.println(buildJson(errors, warnings, parseError));
    }

    private static String buildJson(List<SemanticError> errors, List<SemanticError> warnings,
                                    String parseError) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"errors\":").append(toArray(errors));
        sb.append(",\"warnings\":").append(toArray(warnings));
        if (parseError != null) {
            sb.append(",\"parseError\":\"").append(esc(parseError)).append("\"");
        }
        return sb.append("}").toString();
    }

    private static String toArray(List<SemanticError> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            SemanticError e = items.get(i);
            sb.append("{\"type\":\"").append(e.isError() ? "ERROR" : "WARNING").append("\"")
              .append(",\"line\":").append(e.getLine())
              .append(",\"col\":").append(e.getColumn())
              .append(",\"msg\":\"").append(esc(e.getMessage())).append("\"}");
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
