package output;

import ast.python.PythonNode;
import ast.template.TemplateNode;
import semantic.JinjaAstSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import semantic.SemanticError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes compiler artifacts into {@code output/}:
 * {@code ast_python.json}, {@code ast_jinja.json}, {@code semantic_report.txt}.
 */
public final class CompilerArtifacts {

    private final Path outputDir;
    private String pythonSource;
    private PythonNode pythonAst;
    private PythonSemanticAnalyzer pythonSemantic;
    private final Map<String, TemplateNode> jinjaAsts = new LinkedHashMap<>();
    private final Map<String, JinjaAstSemanticAnalyzer> jinjaSemantics = new LinkedHashMap<>();

    public CompilerArtifacts(Path outputDir) {
        this.outputDir = outputDir;
    }

    public void setPython(String source, PythonNode ast, PythonSemanticAnalyzer semantic) {
        this.pythonSource = source;
        this.pythonAst = ast;
        this.pythonSemantic = semantic;
    }

    public void addJinja(String templateName, TemplateNode ast, JinjaAstSemanticAnalyzer semantic) {
        jinjaAsts.put(templateName, ast);
        jinjaSemantics.put(templateName, semantic);
    }

    public void write() throws IOException {
        Files.createDirectories(outputDir);
        Path py = outputDir.resolve("ast_python.json");
        Path jn = outputDir.resolve("ast_jinja.json");
        Path sem = outputDir.resolve("semantic_report.txt");

        Files.writeString(py, Json.pretty(PythonASTJson.dump(pythonAst, pythonSource)),
                StandardCharsets.UTF_8);

        Map<String, Object> jinjaRoot = new LinkedHashMap<>();
        jinjaRoot.put("templates", jinjaAsts.keySet().stream().toList());
        Map<String, Object> trees = new LinkedHashMap<>();
        for (Map.Entry<String, TemplateNode> e : jinjaAsts.entrySet()) {
            trees.put(e.getKey(), TemplateASTJson.dumpTree(e.getValue()));
        }
        jinjaRoot.put("ast", trees);
        Files.writeString(jn, Json.pretty(jinjaRoot), StandardCharsets.UTF_8);

        Files.writeString(sem, buildSemanticReport(), StandardCharsets.UTF_8);

        System.out.println("  ast_python.json -> " + py.toAbsolutePath().normalize());
        System.out.println("  ast_jinja.json  -> " + jn.toAbsolutePath().normalize());
        System.out.println("  semantic_report.txt -> " + sem.toAbsolutePath().normalize());
    }

    private String buildSemanticReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("SEMANTIC REPORT\n");
        sb.append("=".repeat(60)).append('\n');

        sb.append("\nPYTHON: ").append(pythonSource == null ? "(none)" : pythonSource).append('\n');
        sb.append("-".repeat(60)).append('\n');
        appendAnalyzer(sb, pythonSemantic == null ? List.of() : pythonSemantic.getErrors(),
                pythonSemantic == null ? List.of() : pythonSemantic.getWarnings());

        for (Map.Entry<String, JinjaAstSemanticAnalyzer> e : jinjaSemantics.entrySet()) {
            sb.append("\nJINJA2: ").append(e.getKey()).append('\n');
            sb.append("-".repeat(60)).append('\n');
            appendAnalyzer(sb, e.getValue().getErrors(), e.getValue().getWarnings());
        }
        return sb.toString();
    }

    private static void appendAnalyzer(StringBuilder sb,
                                       List<SemanticError> errors,
                                       List<SemanticError> warnings) {
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("  No semantic issues found.\n");
            return;
        }
        if (!errors.isEmpty()) {
            sb.append("  ERRORS (").append(errors.size()).append("):\n");
            for (SemanticError e : errors) sb.append("  ").append(format(e)).append('\n');
        }
        if (!warnings.isEmpty()) {
            sb.append("  WARNINGS (").append(warnings.size()).append("):\n");
            for (SemanticError w : warnings) sb.append("  ").append(format(w)).append('\n');
        }
        sb.append("  Summary: ").append(errors.size()).append(" error(s), ")
                .append(warnings.size()).append(" warning(s)\n");
    }

    private static String format(SemanticError e) {
        return String.format("[%s] Line %d:%d - %s",
                e.getSeverity(), e.getLine(), e.getColumn(), e.getMessage());
    }
}
