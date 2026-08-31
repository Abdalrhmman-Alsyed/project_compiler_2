import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RunTest3 {
    public static void main(String[] args) throws Exception {
        Files.deleteIfExists(Paths.get("tests/test3_semantic_errors/semantic_log.txt"));
        RunAllTests.runPythonErrorDemo("tests/test3_semantic_errors/app.py", "tests/test3_semantic_errors/semantic_log.txt");
        Set<String> demoCtxVars = new LinkedHashSet<>(List.of("items", "title", "user"));
        RunAllTests.runJinjaErrorDemo("tests/test3_semantic_errors/templates/bad.jinja", demoCtxVars, new HashMap<>(), "tests/test3_semantic_errors/semantic_log.txt");
    }
}
