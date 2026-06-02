package taskmanager;

// Tests d'intégration pour ApiServer
// Démarre un vrai serveur HTTP sur un port libre, envoie des requêtes HTTP réelles
// Lancement : java -cp out taskmanager.ApiServerTest

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class ApiServerTest {

    // Mini framework
    
    private static int passed = 0;
    private static int failed = 0;

    private static void ok(String name) {
        System.out.printf("  [PASS] %s%n", name);
        passed++;
    }

    private static void fail(String name, String reason) {
        System.out.printf("  [FAIL] %s - %s%n", name, reason);
        failed++;
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        if (expected.equals(actual)) ok(name);
        else fail(name, "expected <" + expected + "> got <" + actual + ">");
    }

    private static void assertTrue(String name, boolean cond) {
        if (cond) ok(name); else fail(name, "condition false");
    }

    private static void assertFalse(String name, boolean cond) {
        if (!cond) ok(name); else fail(name, "condition true");
    }

    private static void assertContains(String name, String haystack, String needle) {
        if (haystack.contains(needle)) ok(name);
        else fail(name, "expected to contain <" + needle + "> in <" + haystack + ">");
    }

    private static void assertNotContains(String name, String haystack, String needle) {
        if (!haystack.contains(needle)) ok(name);
        else fail(name, "expected NOT to contain <" + needle + "> in <" + haystack + ">");
    }

    private static void section(String name) {
        System.out.println("\n  -- " + name + " --");
    }

    // HTTP helpers
    
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static int    PORT;
    private static String BASE;

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> put(String path, String body) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> delete(String path) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + path))
                .DELETE().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> options(String path) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    // Server lifecycle
    
    private static ApiServer server;
    private static Path      dataFile;
    private static Path      webRoot;

    private static void startServer() throws Exception {
        // Port libre aléatoire
        try (ServerSocket ss = new ServerSocket(0)) {
            PORT = ss.getLocalPort();
        }
        BASE = "http://localhost:" + PORT;

        dataFile = Files.createTempFile("api_test_", ".json");
        webRoot  = Files.createTempDirectory("api_web_");

        // Un index.html minimal pour tester le static serving
        Files.writeString(webRoot.resolve("index.html"), "<html><body>TaskMgr</body></html>");

        TaskManager manager = new TaskManager(dataFile.toString());
        server = new ApiServer(manager, PORT, webRoot.toString());
        server.start();

        // Laisser le temps au serveur de démarrer
        Thread.sleep(200);
    }

    private static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        
        // Ensure temporary files are cleaned up
        Files.deleteIfExists(dataFile);
        Files.walk(webRoot)
             .sorted(java.util.Comparator.reverseOrder())
             .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
             
        // Hard exit if the thread pool is keeping the process alive
        // (This is a safety net for the test process)
    }

    // Tests GET /api/tasks
    
    static void testGetTasksEmpty() throws Exception {
        section("GET /api/tasks - liste vide");
        var r = get("/api/tasks");
        assertEquals("status 200",      200, r.statusCode());
        assertEquals("body = []",       "[]", r.body().trim());
        assertContains("Content-Type JSON", r.headers().firstValue("Content-Type").orElse(""), "application/json");
    }

    static void testGetTasksAfterPost() throws Exception {
        section("GET /api/tasks - après création");
        post("/api/tasks", "{\"title\":\"A\",\"dueDate\":\"2026-06-01\",\"status\":\"TODO\"}");
        post("/api/tasks", "{\"title\":\"B\",\"dueDate\":\"2026-07-01\",\"status\":\"DOING\"}");

        var r = get("/api/tasks");
        assertEquals("status 200", 200, r.statusCode());
        assertContains("titre A présent", r.body(), "\"A\"");
        assertContains("titre B présent", r.body(), "\"B\"");
    }

    static void testGetTasksFilterTodo() throws Exception {
        section("GET /api/tasks?status=TODO");
        var r = get("/api/tasks?status=TODO");
        assertEquals("status 200", 200, r.statusCode());
        assertContains("TODO présent",   r.body(), "TODO");
        assertNotContains("DOING absent", r.body(), "DOING");
    }

    static void testGetTasksFilterDoing() throws Exception {
        section("GET /api/tasks?status=DOING");
        var r = get("/api/tasks?status=DOING");
        assertEquals("status 200", 200, r.statusCode());
        assertContains("DOING présent",  r.body(), "DOING");
        assertNotContains("TODO absent", r.body(), "TODO");
    }

    static void testGetTasksFilterInvalid() throws Exception {
        section("GET /api/tasks?status=INVALID");
        var r = get("/api/tasks?status=INVALID");
        assertEquals("status 400", 400, r.statusCode());
        assertContains("error dans body", r.body(), "error");
    }

    // Tests POST /api/tasks
    
    static void testPostTaskMinimal() throws Exception {
        section("POST /api/tasks - champs minimaux");
        var r = post("/api/tasks", "{\"title\":\"Minimal\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("titre dans réponse", r.body(), "Minimal");
        assertContains("id dans réponse",    r.body(), "\"id\"");
    }

    static void testPostTaskFull() throws Exception {
        section("POST /api/tasks - tous les champs");
        var r = post("/api/tasks",
            "{\"title\":\"Full task\",\"description\":\"desc complète\",\"dueDate\":\"2026-09-15\",\"status\":\"DOING\"}");
        assertEquals("status 201",          201,    r.statusCode());
        assertContains("description",        r.body(), "desc complète");
        assertContains("statut DOING",       r.body(), "DOING");
        assertContains("date",               r.body(), "2026-09-15");
    }

    static void testPostTaskEmptyTitle() throws Exception {
        section("POST /api/tasks - titre vide → 400");
        var r = post("/api/tasks", "{\"title\":\"\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        assertEquals("status 400", 400, r.statusCode());
        assertContains("error message", r.body(), "error");
    }

    static void testPostTaskNoTitle() throws Exception {
        section("POST /api/tasks - pas de titre → 400");
        var r = post("/api/tasks", "{\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        assertEquals("status 400", 400, r.statusCode());
    }

    static void testPostTaskSpecialChars() throws Exception {
        section("POST /api/tasks - guillemets et backslash");
        var r = post("/api/tasks",
            "{\"title\":\"Titre avec \\\"guillemets\\\"\",\"description\":\"desc avec \\\\\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("guillemets dans réponse", r.body(), "guillemets");
    }

    static void testPostTaskDefaultStatus() throws Exception {
        section("POST /api/tasks - statut par défaut TODO");
        var r = post("/api/tasks", "{\"title\":\"No status\",\"dueDate\":\"2026-01-01\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("statut TODO par défaut", r.body(), "TODO");
    }

    // Tests PUT /api/tasks/{id}
    
    static void testPutTaskTitle() throws Exception {
        section("PUT /api/tasks/{id} - modifier le titre");
        var created = post("/api/tasks", "{\"title\":\"Original\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        int id = extractId(created.body());

        var r = put("/api/tasks/" + id, "{\"title\":\"Modifié\"}");
        assertEquals("status 200",          200, r.statusCode());
        assertContains("nouveau titre",      r.body(), "Modifi");
    }

    static void testPutTaskStatus() throws Exception {
        section("PUT /api/tasks/{id} - modifier le statut");
        var created = post("/api/tasks", "{\"title\":\"StatusTest\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        int id = extractId(created.body());

        var r = put("/api/tasks/" + id, "{\"status\":\"DONE\"}");
        assertEquals("status 200",    200,    r.statusCode());
        assertContains("statut DONE", r.body(), "DONE");
    }

    static void testPutTaskPartial() throws Exception {
        section("PUT /api/tasks/{id} - mise à jour partielle (null fields)");
        var created = post("/api/tasks",
            "{\"title\":\"Partiel\",\"description\":\"desc originale\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        int id = extractId(created.body());

        var r = put("/api/tasks/" + id, "{\"status\":\"DOING\"}");
        assertEquals("status 200",         200, r.statusCode());
        assertContains("statut mis à jour", r.body(), "DOING");
        assertContains("titre conservé",    r.body(), "Partiel");
    }

    static void testPutTaskNotFound() throws Exception {
        section("PUT /api/tasks/99999 - ID inexistant → 404");
        var r = put("/api/tasks/99999", "{\"title\":\"Ghost\"}");
        assertEquals("status 404", 404, r.statusCode());
    }

    static void testPutTaskMissingId() throws Exception {
        section("PUT /api/tasks - sans ID → 400");
        var r = put("/api/tasks", "{\"title\":\"No ID\"}");
        assertEquals("status 400", 400, r.statusCode());
    }

    static void testPutTaskInvalidId() throws Exception {
        section("PUT /api/tasks/abc - ID non numérique → 400");
        var r = put("/api/tasks/abc", "{\"title\":\"Bad ID\"}");
        assertEquals("status 400", 400, r.statusCode());
    }

    // Tests DELETE /api/tasks/{id}
    
    static void testDeleteTask() throws Exception {
        section("DELETE /api/tasks/{id} - suppression");
        var created = post("/api/tasks", "{\"title\":\"ToDelete\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        int id = extractId(created.body());

        var r = delete("/api/tasks/" + id);
        assertEquals("status 200",           200, r.statusCode());
        assertContains("deleted dans body",  r.body(), "deleted");

        // Vérifier que la tâche n'existe plus
        var check = get("/api/tasks");
        assertNotContains("tâche absente de la liste", check.body(), "ToDelete");
    }

    static void testDeleteTaskNotFound() throws Exception {
        section("DELETE /api/tasks/99999 - ID inexistant → 404");
        var r = delete("/api/tasks/99999");
        assertEquals("status 404", 404, r.statusCode());
    }

    static void testDeleteTaskMissingId() throws Exception {
        section("DELETE /api/tasks - sans ID → 400");
        var r = delete("/api/tasks");
        assertEquals("status 400", 400, r.statusCode());
    }

    // Tests GET /api/stats
    
    static void testStats() throws Exception {
        section("GET /api/stats - compteurs corrects");
        var r = get("/api/stats");
        assertEquals("status 200", 200, r.statusCode());
        assertContains("total",    r.body(), "\"total\"");
        assertContains("todo",     r.body(), "\"todo\"");
        assertContains("doing",    r.body(), "\"doing\"");
        assertContains("done",     r.body(), "\"done\"");
    }

    static void testStatsValues() throws Exception {
        section("GET /api/stats - valeurs cohérentes avec les tâches");
        var tasks = get("/api/tasks");
        var stats = get("/api/stats");
        assertEquals("stats status 200", 200, stats.statusCode());

        // Le total dans les stats doit être > 0 si on a des tâches
        if (!tasks.body().equals("[]")) {
            assertFalse("total > 0 si tâches existent", stats.body().contains("\"total\":0"));
        }
    }

    // Tests fichiers statiques
    
    static void testStaticIndex() throws Exception {
        section("GET / - sert index.html");
        var r = get("/");
        assertEquals("status 200",     200, r.statusCode());
        assertContains("contenu HTML", r.body(), "TaskMgr");
    }

    static void testStaticNotFound() throws Exception {
        section("GET /inexistant.html → 404");
        var r = get("/inexistant.html");
        assertEquals("status 404", 404, r.statusCode());
    }

    // Tests CORS
    
    static void testCorsHeaders() throws Exception {
        section("CORS - headers présents sur /api/tasks");
        var r = get("/api/tasks");
        String origin = r.headers().firstValue("Access-Control-Allow-Origin").orElse("");
        assertFalse("Access-Control-Allow-Origin présent", origin.isEmpty());
    }

    static void testOptionsPreflightTasks() throws Exception {
        section("CORS - OPTIONS preflight → 204");
        var r = options("/api/tasks");
        assertEquals("status 204", 204, r.statusCode());
    }

    static void testOptionsPreflightStats() throws Exception {
        section("CORS - OPTIONS preflight /api/stats → 204");
        var r = options("/api/stats");
        assertEquals("status 204", 204, r.statusCode());
    }

    // Tests méthode non supportée
    
    static void testMethodNotAllowed() throws Exception {
        section("PATCH /api/tasks → 405");
        var r = HTTP.send(
            HttpRequest.newBuilder(URI.create(BASE + "/api/tasks"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals("status 405", 405, r.statusCode());
    }

    // Tests de flux complets (end-to-end)
    
    static void testFullCrudCycle() throws Exception {
        section("End-to-end - cycle CRUD complet");

        // Créer
        var created = post("/api/tasks",
            "{\"title\":\"E2E task\",\"description\":\"test complet\",\"dueDate\":\"2026-12-31\",\"status\":\"TODO\"}");
        assertEquals("création 201", 201, created.statusCode());
        int id = extractId(created.body());
        assertTrue("ID valide", id > 0);

        // Lire
        var list = get("/api/tasks");
        assertContains("tâche dans la liste", list.body(), "E2E task");

        // Modifier
        var updated = put("/api/tasks/" + id, "{\"status\":\"DOING\",\"title\":\"E2E modifié\"}");
        assertEquals("modification 200", 200, updated.statusCode());
        assertContains("nouveau titre", updated.body(), "E2E modifi");
        assertContains("nouveau statut", updated.body(), "DOING");

        // Vérifier les stats
        var stats = get("/api/stats");
        assertContains("doing dans stats", stats.body(), "\"doing\"");

        // Supprimer
        var deleted = delete("/api/tasks/" + id);
        assertEquals("suppression 200", 200, deleted.statusCode());

        // Vérifier suppression
        var after = get("/api/tasks");
        assertNotContains("tâche absente", after.body(), "E2E modifi");
    }

    static void testMultipleTasksOrdering() throws Exception {
        section("End-to-end - ordre de création préservé");

        var r1 = post("/api/tasks", "{\"title\":\"Premier\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        var r2 = post("/api/tasks", "{\"title\":\"Deuxième\",\"dueDate\":\"2026-01-02\",\"status\":\"TODO\"}");
        var r3 = post("/api/tasks", "{\"title\":\"Troisième\",\"dueDate\":\"2026-01-03\",\"status\":\"TODO\"}");

        int id1 = extractId(r1.body());
        int id2 = extractId(r2.body());
        int id3 = extractId(r3.body());

        assertTrue("IDs croissants", id1 < id2 && id2 < id3);

        var list = get("/api/tasks");
        int pos1 = list.body().indexOf("Premier");
        int pos2 = list.body().indexOf("Deuxième");
        int pos3 = list.body().indexOf("Troisième");

        assertTrue("ordre préservé dans la liste", pos1 < pos2 && pos2 < pos3);
    }

    static void testStatsConsistency() throws Exception {
        section("End-to-end - cohérence stats / liste");

        // Compter manuellement depuis la liste
        var list  = get("/api/tasks");
        var stats = get("/api/stats");

        // Extraire le total des stats
        int statTotal = extractIntField(stats.body(), "total");

        // Compter les occurrences de "id" dans la liste comme proxy du nombre de tâches
        int countInList = countOccurrences(list.body(), "\"id\"");

        assertEquals("total stats == nb tâches dans liste", statTotal, countInList);
    }


    // Tests Priority via API
    
    static void testPostTaskWithPriorityHigh() throws Exception {
        section("POST /api/tasks - priorité HIGH");
        var r = post("/api/tasks",
            "{\"title\":\"Urgent\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\",\"priority\":\"HIGH\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("priority HIGH dans réponse", r.body(), "HIGH");
    }

    static void testPostTaskWithPriorityLow() throws Exception {
        section("POST /api/tasks - priorité LOW");
        var r = post("/api/tasks",
            "{\"title\":\"Optional\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\",\"priority\":\"LOW\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("priority LOW dans réponse", r.body(), "LOW");
    }

    static void testPostTaskDefaultPriority() throws Exception {
        section("POST /api/tasks - sans priorité → MEDIUM");
        var r = post("/api/tasks",
            "{\"title\":\"Default prio\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\"}");
        assertEquals("status 201", 201, r.statusCode());
        assertContains("MEDIUM par défaut", r.body(), "MEDIUM");
    }

    static void testPutTaskPriorityOnly() throws Exception {
        section("PUT /api/tasks/{id} - modifier priorité seulement");
        var created = post("/api/tasks",
            "{\"title\":\"PrioTest\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\",\"priority\":\"LOW\"}");
        int id = extractId(created.body());

        var r = put("/api/tasks/" + id, "{\"priority\":\"HIGH\"}");
        assertEquals("status 200",      200, r.statusCode());
        assertContains("priorité HIGH", r.body(), "HIGH");
        assertContains("titre conservé", r.body(), "PrioTest");
    }

    static void testPriorityInGetTasks() throws Exception {
        section("GET /api/tasks - priority présent dans chaque tâche");
        var r = get("/api/tasks");
        assertEquals("status 200", 200, r.statusCode());
        if (!r.body().equals("[]")) {
            assertContains("champ priority présent", r.body(), "\"priority\"");
        }
    }

    // Tests GET /api/export
    
    static void testExportCsvStatus() throws Exception {
        section("GET /api/export - status 200");
        var r = get("/api/export");
        assertEquals("status 200", 200, r.statusCode());
    }

    static void testExportCsvContentType() throws Exception {
        section("GET /api/export - Content-Type text/csv");
        var r = get("/api/export");
        String ct = r.headers().firstValue("Content-Type").orElse("");
        assertContains("Content-Type csv", ct, "text/csv");
    }

    static void testExportCsvHeader() throws Exception {
        section("GET /api/export - en-tête CSV correct");
        var r = get("/api/export");
        assertTrue("CSV commence par en-tête",
            r.body().startsWith("id,title,description,dueDate,status,priority"));
    }

    static void testExportCsvContainsTasks() throws Exception {
        section("GET /api/export - tâches présentes dans le CSV");
        post("/api/tasks",
            "{\"title\":\"CSV export task\",\"dueDate\":\"2026-06-01\",\"status\":\"TODO\",\"priority\":\"HIGH\"}");

        var r = get("/api/export");
        assertContains("titre dans CSV",    r.body(), "CSV export task");
        assertContains("statut dans CSV",   r.body(), "TODO");
        assertContains("priorité dans CSV", r.body(), "HIGH");
    }

    static void testExportCsvCommaInTitle() throws Exception {
        section("GET /api/export - virgule dans titre → guillemets CSV");
        post("/api/tasks",
            "{\"title\":\"Titre, avec virgule\",\"dueDate\":\"2026-01-01\",\"status\":\"TODO\",\"priority\":\"LOW\"}");

        var r = get("/api/export");
        assertContains("virgule entre guillemets", r.body(), "\"Titre, avec virgule\"");
    }

    static void testExportCsvMethodNotAllowed() throws Exception {
        section("POST /api/export → 405");
        var r = post("/api/export", "{}");
        assertEquals("status 405", 405, r.statusCode());
    }

    // Helpers
    
    private static int extractId(String json) {
        int idx = json.indexOf("\"id\":");
        if (idx == -1) return -1;
        int start = idx + 5;
        int end   = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    private static int extractIntField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return -1;
        int start = idx + search.length();
        int end   = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }

    // Main
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n  ========================================");
        System.out.println("  APISERVER - TESTS D'INTÉGRATION");
        System.out.println("  ========================================");

        startServer();
        System.out.println("  Serveur de test démarré sur port " + PORT);

        try {
            // GET /api/tasks
            testGetTasksEmpty();
            testGetTasksAfterPost();
            testGetTasksFilterTodo();
            testGetTasksFilterDoing();
            testGetTasksFilterInvalid();

            // POST /api/tasks
            testPostTaskMinimal();
            testPostTaskFull();
            testPostTaskEmptyTitle();
            testPostTaskNoTitle();
            testPostTaskSpecialChars();
            testPostTaskDefaultStatus();

            // PUT /api/tasks/{id}
            testPutTaskTitle();
            testPutTaskStatus();
            testPutTaskPartial();
            testPutTaskNotFound();
            testPutTaskMissingId();
            testPutTaskInvalidId();

            // DELETE
            testDeleteTask();
            testDeleteTaskNotFound();
            testDeleteTaskMissingId();

            // Stats
            testStats();
            testStatsValues();

            // Static
            testStaticIndex();
            testStaticNotFound();

            // CORS
            testCorsHeaders();
            testOptionsPreflightTasks();
            testOptionsPreflightStats();

            // Method not allowed
            testMethodNotAllowed();

            // End-to-end
            testFullCrudCycle();
            testMultipleTasksOrdering();
            testStatsConsistency();


            // Tests Priority
            testPostTaskWithPriorityHigh();
            testPostTaskWithPriorityLow();
            testPostTaskDefaultPriority();
            testPutTaskPriorityOnly();
            testPriorityInGetTasks();

            // Tests Export CSV
            testExportCsvStatus();
            testExportCsvContentType();
            testExportCsvHeader();
            testExportCsvContainsTasks();
            testExportCsvCommaInTitle();
            testExportCsvMethodNotAllowed();

        } finally {
            stopServer();
        }

        System.out.println("\n  ========================================");
        System.out.printf("  %d/%d tests passés%n", passed, passed + failed);
        System.out.println("  ========================================\n");

        if (failed > 0) System.exit(1);
        System.exit(0);
    }
}
