package taskmanager;

// Félix Vandenbroucke - Dev 2026
// Serveur HTTP minimal basé sur com.sun.net.httpserver (inclus dans le JDK)
// Endpoints REST JSON :
//   GET    /api/tasks          liste toutes les tâches (optionnel: ?status=TODO)
//   POST   /api/tasks          crée une tâche
//   PUT    /api/tasks/{id}     modifie une tâche
//   DELETE /api/tasks/{id}     supprime une tâche
//   GET    /api/stats          statistiques par statut
//   GET    /                   sert web/index.html
//   GET    /index.html         idem

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ApiServer {

    private final TaskManager manager;
    private final int         port;
    private final Path        webRoot;
    private HttpServer        httpServer;
    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    public ApiServer(TaskManager manager, int port, String webRootPath) {
        this.manager = manager;
        this.port    = port;
        this.webRoot = Paths.get(webRootPath);
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        httpServer.createContext("/api/tasks",  this::handleTasks);
        httpServer.createContext("/api/export", this::handleExport);
        httpServer.createContext("/api/stats", this::handleStats);
        httpServer.createContext("/",          this::handleStatic);

        // Parallel processing of requests
        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();

        System.out.println("[INFO] Serveur démarré sur http://localhost:" + port);
    }

    public void stop() {
        if (httpServer != null) httpServer.stop(0);
    }

    // /api/tasks - dispatch selon méthode et présence d'un ID dans l'URL
    
    private void handleTasks(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }

        String path   = ex.getRequestURI().getPath();      // /api/tasks  ou  /api/tasks/42
        String[] parts = path.split("/");
        boolean hasId  = parts.length == 4 && !parts[3].isEmpty();

        try {
            switch (ex.getRequestMethod()) {
                case "GET"    -> handleGetTasks(ex);
                case "POST"   -> handlePostTask(ex);
                case "PUT"    -> { if (hasId) handlePutTask(ex, Integer.parseInt(parts[3])); else respond(ex, 400, error("ID manquant")); }
                case "DELETE" -> { if (hasId) handleDeleteTask(ex, Integer.parseInt(parts[3])); else respond(ex, 400, error("ID manquant")); }
                default       -> respond(ex, 405, error("Méthode non supportée"));
            }
        } catch (NumberFormatException e) {
            respond(ex, 400, error("ID invalide"));
        } catch (Exception e) {
            LOG.severe("Erreur interne : " + e.getMessage());
            respond(ex, 500, error("Erreur interne"));
        }
    }

    private void handleGetTasks(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        List<Task> tasks;

        if (query != null && query.startsWith("status=")) {
            String statusStr = query.substring(7).toUpperCase();
            try {
                Task.Status filter = Task.Status.parseStatus(statusStr);
                tasks = manager.getTasksByStatus(filter);
            } catch (IllegalArgumentException e) {
                respond(ex, 400, error("Statut invalide : " + statusStr));
                return;
            }
        } else {
            tasks = manager.getAllTasks();
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            sb.append(tasks.get(i).toJson());
            if (i < tasks.size() - 1) sb.append(",");
        }
        sb.append("]");

        respondJson(ex, 200, sb.toString());
    }

    private void handlePostTask(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        try {
            String title       = extractJsonString(body, "title");
            String description = extractJsonString(body, "description");
            String dueDateStr  = extractJsonString(body, "dueDate");
            String statusStr   = extractJsonString(body, "status");

            if (title == null || title.isBlank()) {
                respond(ex, 400, error("Le titre est obligatoire"));
                return;
            }

            LocalDate   dueDate = dueDateStr != null && !dueDateStr.isBlank()
                                    ? LocalDate.parse(dueDateStr)
                                    : LocalDate.now().plusDays(1);
            Task.Status status  = statusStr != null && !statusStr.isBlank()
                                    ? Task.Status.parseStatus(statusStr)
                                    : Task.Status.TODO;
            String priorityStr = extractJsonString(body, "priority");
            Task.Priority priority = priorityStr != null && !priorityStr.isBlank()
                                    ? Task.Priority.parsePriority(priorityStr)
                                    : Task.Priority.MEDIUM;

            Task created = manager.addTask(title, description != null ? description : "", dueDate, status, priority);
            respondJson(ex, 201, created.toJson());

        } catch (Exception e) {
            respond(ex, 400, error("Corps JSON invalide : " + e.getMessage()));
        }
    }

    private void handlePutTask(HttpExchange ex, int id) throws IOException {
        String body = readBody(ex);
        try {
            String title       = extractJsonString(body, "title");
            String description = extractJsonString(body, "description");
            String dueDateStr  = extractJsonString(body, "dueDate");
            String statusStr   = extractJsonString(body, "status");

            LocalDate   dueDate = dueDateStr != null && !dueDateStr.isBlank()
                                    ? LocalDate.parse(dueDateStr) : null;
            Task.Status status  = statusStr != null && !statusStr.isBlank()
                                    ? Task.Status.parseStatus(statusStr) : null;

            String newTitle = (title != null && !title.isBlank()) ? title : null;
            String newDesc  = description;
            String priorityStr = extractJsonString(body, "priority");
            Task.Priority priority = priorityStr != null && !priorityStr.isBlank()
                                    ? Task.Priority.parsePriority(priorityStr) : null;

            boolean ok = manager.updateTask(id, newTitle, newDesc, dueDate, status, priority);
            if (!ok) {
                respond(ex, 404, error("Tâche introuvable : " + id));
                return;
            }

            Task updated = manager.findTaskById(id).get();
            respondJson(ex, 200, updated.toJson());

        } catch (Exception e) {
            respond(ex, 400, error("Corps JSON invalide : " + e.getMessage()));
        }
    }

    private void handleDeleteTask(HttpExchange ex, int id) throws IOException {
        boolean ok = manager.deleteTask(id);
        if (ok) {
            respondJson(ex, 200, "{\"deleted\":" + id + "}");
        } else {
            respond(ex, 404, error("Tâche introuvable : " + id));
        }
    }

    // /api/export - exporte toutes les tâches au format CSV

    private void handleExport(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"GET".equals(ex.getRequestMethod()))    { respond(ex, 405, error("Méthode non supportée")); return; }

        String csv = manager.exportCsv();
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",        "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=tasks.csv");
        ex.sendResponseHeaders(200, bytes.length);
        try (java.io.OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        LOG.info("Export CSV déclenché : " + manager.getTotalTaskCount() + " tâche(s)");
    }

    // /api/stats
    
    private void handleStats(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"GET".equals(ex.getRequestMethod()))    { respond(ex, 405, error("Méthode non supportée")); return; }

        int todo  = manager.countTasksByStatus(Task.Status.TODO);
        int doing = manager.countTasksByStatus(Task.Status.DOING);
        int done  = manager.countTasksByStatus(Task.Status.DONE);
        int total = manager.getTotalTaskCount();

        String json = String.format(
            "{\"total\":%d,\"todo\":%d,\"doing\":%d,\"done\":%d}",
            total, todo, doing, done
        );
        respondJson(ex, 200, json);
    }

    // Fichiers statiques - sert web/index.html
    
    private void handleStatic(HttpExchange ex) throws IOException {
        String uriPath = ex.getRequestURI().getPath();
        if (uriPath.equals("/") || uriPath.equals("/index.html")) {
            uriPath = "/index.html";
        }

        Path file = webRoot.resolve(uriPath.substring(1));

        if (!Files.exists(file) || Files.isDirectory(file)) {
            respond(ex, 404, "Not found");
            return;
        }

        String contentType = "text/plain";
        if (uriPath.endsWith(".html")) contentType = "text/html; charset=utf-8";
        else if (uriPath.endsWith(".css"))  contentType = "text/css";
        else if (uriPath.endsWith(".js"))   contentType = "application/javascript";

        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // Utilitaires
    
    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void respondJson(HttpExchange ex, int status, String json) throws IOException {
        respond(ex, status, json, "application/json; charset=utf-8");
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        respond(ex, status, body, "text/plain; charset=utf-8");
    }

    private void respond(HttpExchange ex, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String error(String msg) {
        return "{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}";
    }

    // Extraction minimaliste d'une valeur string depuis du JSON plat
    // Suffisant pour mon format - pas de JSON générique imbriqué
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return null;

        int colon = json.indexOf(':', keyIdx + search.length());
        if (colon == -1) return null;

        int start = json.indexOf('"', colon + 1);
        if (start == -1) return null;

        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if      (next == '"')  { sb.append('"');  i += 2; }
                else if (next == '\\') { sb.append('\\'); i += 2; }
                else if (next == 'n')  { sb.append('\n'); i += 2; }
                else                   { sb.append(c);    i++; }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
