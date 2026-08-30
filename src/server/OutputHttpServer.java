package server;
import ast.python.PythonNode;
import ast.python.visitors.PythonASTBuilderVisitor;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gen.FlaskPythonLexer;
import gen.FlaskPythonParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import semantic.Generator;
import codeGenerator.HtmlCodeGenerator;
import semantic.MockDataExtractor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Serves {@code output/} and applies add/delete by updating the live product
 * list, then re-running {@link HtmlCodeGenerator}.
 *
 * <pre>
 * java Main                 # compiler pipeline, then this server on 8080
 * java Main 8080
 * java OutputHttpServer     # server only (also writes Generated and wipes the log)
 * </pre>
 */
public class OutputHttpServer {

    private final int port;
    private final Path outputDir = Path.of("output");
    private final Path staticDir = Path.of("flask-app", "static");

    private Generator generator;
    private HtmlCodeGenerator htmlGen;
    private final Map<String, Object> bound = new LinkedHashMap<>();
    private List<Map<String, Object>> products = new ArrayList<>();
    private int nextId = 1;

    public OutputHttpServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        new OutputHttpServer(port).start();
    }

    private HttpServer http;

    public void start() throws Exception {
        loadContextFromPython();
        htmlGen = new HtmlCodeGenerator(Path.of("flask-app", "templates"), outputDir);
        htmlGen.loadTemplates();
        regenerate("HTTP server startup -- first create of HTML pages", true);
        listen();
        awaitStop();
    }

    /**
     * Starts HTTP using the compiler's already-built generator, templates, and
     * bound data. Does not regenerate or wipe the log — {@code Main} already
     * wrote the {@code Generated} entry. Returns after the port is bound;
     * call {@link #awaitStop()} so the process does not exit.
     */
    public void startServing(Generator generator, HtmlCodeGenerator htmlGen,
                             Map<String, Object> bound) throws Exception {
        this.generator = generator;
        this.htmlGen = htmlGen;
        this.bound.clear();
        if (bound != null) this.bound.putAll(bound);
        adoptProductsFromBound();
        listen();
    }

    public void awaitStop() throws InterruptedException {
        Thread.currentThread().join();
    }

    private void listen() throws IOException {
        try {
            http = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (java.net.BindException e) {
            throw new IOException(
                    "Port " + port + " is already in use. Stop the other Java process, then run Main again.",
                    e);
        }
        http.createContext("/", this::handle);
        http.setExecutor(null);
        http.start();
        System.out.println("Java store server: http://localhost:" + port + "/");
        System.out.println("Serving regenerated HTML from " + outputDir.toAbsolutePath());
        System.out.println("Add:  GET/POST /add     Delete: POST /delete/{id}");
        System.out.println("Leave this window open. Stop with Ctrl+C");
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            if ("POST".equalsIgnoreCase(method) && path.equals("/add")) {
                handleAdd(ex);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.startsWith("/delete/")) {
                handleDelete(ex, parseId(path.substring("/delete/".length())));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.equals("/") || path.equals("/index.html")
                    || path.equals("/products"))) {
                serveFile(ex, outputDir.resolve("index.html"), "text/html; charset=utf-8");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.equals("/add") || path.equals("/add_product.html"))) {
                serveFile(ex, outputDir.resolve("add_product.html"), "text/html; charset=utf-8");
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.startsWith("/products/")) {
                handleDetail(ex, parseId(path.substring("/products/".length())));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.equals("/product_detail.html")) {
                serveFile(ex, outputDir.resolve("product_detail.html"), "text/html; charset=utf-8");
                return;
            }

            Path file = resolvePublicFile(path);
            if (file != null && Files.isRegularFile(file)) {
                serveFile(ex, file, contentType(file));
                return;
            }
            send(ex, 404, "text/plain; charset=utf-8", "Not found: " + path);
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "text/plain; charset=utf-8", "Server error: " + e.getMessage());
        }
    }

    private void handleAdd(HttpExchange ex) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, FilePart> files = new LinkedHashMap<>();
        parseBody(ex, fields, files);

        String name = fields.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            send(ex, 400, "text/plain; charset=utf-8", "name is required");
            return;
        }

        String imageName = "default.png";
        FilePart image = files.get("image_file");
        if (image != null && image.filename != null && !image.filename.isBlank() && image.data.length > 0) {
            imageName = sanitizeFilename(image.filename);
            Files.createDirectories(outputDir.resolve("static"));
            Files.write(outputDir.resolve("static").resolve(imageName), image.data);
            Files.createDirectories(staticDir);
            Files.write(staticDir.resolve(imageName), image.data);
        }

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", nextId++);
        product.put("name", name);
        product.put("price", parsePrice(fields.get("price")));
        product.put("details", fields.getOrDefault("details", ""));
        product.put("image_filename", imageName);
        products.add(product);

        regenerate("ADD product (id=" + product.get("id") + ", name=" + name + ")", false);
        redirect(ex, "/");
    }

    private void handleDelete(HttpExchange ex, Integer id) throws IOException {
        if (id == null) {
            send(ex, 400, "text/plain; charset=utf-8", "invalid product id");
            return;
        }
        Map<String, Object> removed = findProduct(id);
        products.removeIf(p -> idEquals(p.get("id"), id));
        if (removed != null) {
            Object fn = removed.get("image_filename");
            if (fn instanceof String name && !name.isBlank() && !"default.png".equals(name)) {
                Files.deleteIfExists(outputDir.resolve("static").resolve(name));
            }
        }
        String removedName = removed != null && removed.get("name") != null
                ? String.valueOf(removed.get("name")) : "?";
        regenerate("DELETE product (id=" + id + ", name=" + removedName + ")", false);
        redirect(ex, "/");
    }

    private void handleDetail(HttpExchange ex, Integer id) throws IOException {
        if (id == null) {
            send(ex, 400, "text/plain; charset=utf-8", "invalid product id");
            return;
        }
        Map<String, Object> product = findProduct(id);
        if (product == null) {
            send(ex, 404, "text/plain; charset=utf-8", "Product Not Found");
            return;
        }
        syncBound();
        bound.put("product", product);
        htmlGen.write("product_detail.jinja",
                htmlGen.contextFor("product_detail.jinja", generator, bound),
                "VIEW product detail (id=" + id + ", name=" + product.get("name") + ")");
        serveFile(ex, outputDir.resolve("product_detail.html"), "text/html; charset=utf-8");
    }

    private synchronized void regenerate(String reason, boolean resetLog) throws IOException {
        syncBound();
        if (!products.isEmpty()) {
            bound.put("product", products.get(0));
        } else {
            bound.remove("product");
        }
        htmlGen.generateRenderedPages(generator, bound, reason, resetLog);
        System.out.println("[regen] " + reason + " -> " + products.size() + " product(s)");
    }

    private void syncBound() {
        bound.put("products", products);
        bound.put("PRODUCTS_BASE_DATA", products);
        bound.put("next_id", nextId);
    }

    private void loadContextFromPython() throws IOException {
        FlaskPythonParser parser = new FlaskPythonParser(new CommonTokenStream(
                new FlaskPythonLexer(CharStreams.fromFileName("flask-app/app.py"))));
        FlaskPythonParser.ProgramContext tree = parser.program();
        PythonNode ast = new PythonASTBuilderVisitor().visit(tree);

        generator = new Generator();
        generator.visit(tree);

        MockDataExtractor extractor = new MockDataExtractor();
        ast.accept(extractor);
        bound.clear();
        bound.putAll(generator.bind(extractor.getExtractedData()));
        adoptProductsFromBound();
    }

    @SuppressWarnings("unchecked")
    private void adoptProductsFromBound() {
        Object raw = bound.get("products");
        if (raw == null) raw = bound.get("PRODUCTS_BASE_DATA");
        products = new ArrayList<>();
        nextId = 1;
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    products.add(copy);
                    Object id = copy.get("id");
                    if (id instanceof Number n) nextId = Math.max(nextId, n.intValue() + 1);
                }
            }
        }
        syncBound();
    }

    private Map<String, Object> findProduct(int id) {
        for (Map<String, Object> p : products) {
            if (idEquals(p.get("id"), id)) return p;
        }
        return null;
    }

    private static boolean idEquals(Object value, int id) {
        return value instanceof Number n && n.intValue() == id;
    }

    private static Integer parseId(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String sanitizeFilename(String name) {
        String base = Path.of(name.replace('\\', '/')).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path resolvePublicFile(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        if (rel.isBlank() || rel.contains("..")) return null;
        Path candidate = outputDir.resolve(rel).normalize();
        if (!candidate.startsWith(outputDir.toAbsolutePath().normalize())
                && !candidate.startsWith(outputDir.normalize())) {
            Path absOut = outputDir.toAbsolutePath().normalize();
            if (!candidate.toAbsolutePath().normalize().startsWith(absOut)) return null;
        }
        return candidate;
    }

    private static String contentType(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private static void serveFile(HttpExchange ex, Path file, String type) throws IOException {
        if (!Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void parseBody(HttpExchange ex, Map<String, String> fields, Map<String, FilePart> files)
            throws IOException {
        String contentType = header(ex, "Content-Type");
        byte[] body = ex.getRequestBody().readAllBytes();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            parseMultipart(body, contentType, fields, files);
            return;
        }
        String decoded = URLDecoder.decode(new String(body, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        for (String pair : decoded.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq < 0) fields.put(pair, "");
            else fields.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
    }

    private static String header(HttpExchange ex, String name) {
        Headers h = ex.getRequestHeaders();
        List<String> vals = h.get(name);
        return vals == null || vals.isEmpty() ? null : vals.get(0);
    }

    private static void parseMultipart(byte[] body, String contentType,
                                       Map<String, String> fields, Map<String, FilePart> files) {
        String boundary = null;
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                boundary = p.substring("boundary=".length()).replace("\"", "");
            }
        }
        if (boundary == null || boundary.isBlank()) return;
        byte[] dashBoundary = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = indexOf(body, dashBoundary, 0);
        while (pos >= 0) {
            int start = pos + dashBoundary.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') break;
            if (start + 1 < body.length && body[start] == '\r') start += 2;
            else if (start < body.length && body[start] == '\n') start += 1;
            int next = indexOf(body, dashBoundary, start);
            if (next < 0) break;
            int partEnd = next;
            if (partEnd >= 2 && body[partEnd - 2] == '\r' && body[partEnd - 1] == '\n') partEnd -= 2;
            else if (partEnd >= 1 && body[partEnd - 1] == '\n') partEnd -= 1;
            parseOnePart(Arrays.copyOfRange(body, start, Math.max(start, partEnd)), fields, files);
            pos = next;
        }
    }

    private static void parseOnePart(byte[] part, Map<String, String> fields, Map<String, FilePart> files) {
        int sep = indexOf(part, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
        int headerLen = 4;
        if (sep < 0) {
            sep = indexOf(part, "\n\n".getBytes(StandardCharsets.ISO_8859_1), 0);
            headerLen = 2;
        }
        if (sep < 0) return;
        String headers = new String(part, 0, sep, StandardCharsets.ISO_8859_1);
        byte[] data = Arrays.copyOfRange(part, sep + headerLen, part.length);
        String disp = "";
        for (String line : headers.split("\r?\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) {
                disp = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        String fieldName = headerParam(disp, "name");
        String filename = headerParam(disp, "filename");
        if (fieldName == null) return;
        if (filename != null && !filename.isBlank()) {
            files.put(fieldName, new FilePart(filename, data));
        } else {
            fields.put(fieldName, new String(data, StandardCharsets.UTF_8));
        }
    }

    private static String headerParam(String header, String key) {
        for (String piece : header.split(";")) {
            String p = piece.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT) + "=")) {
                String v = p.substring(key.length() + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = from; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static final class FilePart {
        final String filename;
        final byte[] data;

        FilePart(String filename, byte[] data) {
            this.filename = filename;
            this.data = data;
        }
    }
}
