package taskmanager;

// Tests autonomes - pas de dépendance externe (pas de JUnit)
// Lancement : java -cp out taskmanager.TaskManagerTest

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

public class TaskManagerTest {

    // Mini framework d'assertions
    private static int passed = 0;
    private static int failed = 0;

    private static void ok(String testName) {
        System.out.printf("  [PASS] %s%n", testName);
        passed++;
    }

    private static void fail(String testName, String reason) {
        System.out.printf("  [FAIL] %s - %s%n", testName, reason);
        failed++;
    }

    private static void assertEquals(String test, Object expected, Object actual) {
        if (expected.equals(actual)) {
            ok(test);
        } else {
            fail(test, "expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertTrue(String test, boolean condition) {
        if (condition) ok(test); else fail(test, "condition was false");
    }

    private static void assertFalse(String test, boolean condition) {
        if (!condition) ok(test); else fail(test, "condition was true");
    }

    private static void section(String name) {
        System.out.println("\n  -- " + name + " --");
    }

    // Helpers

    private static Path tmpFile() throws IOException {
        return Files.createTempFile("taskmanager_test_", ".json");
    }

    // Tests Task.toJson / Task.fromJson
    
    static void testJsonRoundtrip() {
        section("Task - sérialisation JSON");

        Task original = new Task(1, "Titre simple", "Description", LocalDate.of(2025, 6, 1), Task.Status.TODO);
        Task restored = Task.fromJson(original.toJson());

        assertEquals("id préservé",          original.getId(),          restored.getId());
        assertEquals("titre préservé",       original.getTitle(),       restored.getTitle());
        assertEquals("description préservée",original.getDescription(), restored.getDescription());
        assertEquals("date préservée",       original.getDueDate(),     restored.getDueDate());
        assertEquals("statut préservé",      original.getStatus(),      restored.getStatus());
    }

    static void testJsonEscaping() {
        section("Task - caractères spéciaux dans JSON");

        Task t = new Task(2, "Titre avec \"guillemets\"", "Desc avec \\backslash", LocalDate.of(2025, 1, 1), Task.Status.DOING);
        Task restored = Task.fromJson(t.toJson());

        assertEquals("guillemets roundtrip",  "Titre avec \"guillemets\"", restored.getTitle());
        assertEquals("backslash roundtrip",   "Desc avec \\backslash",    restored.getDescription());
    }

    static void testJsonAllStatuses() {
        section("Task - tous les statuts");

        for (Task.Status s : Task.Status.values()) {
            Task t = new Task(3, "T", "D", LocalDate.now(), s);
            Task restored = Task.fromJson(t.toJson());
            assertEquals("statut " + s + " roundtrip", s, restored.getStatus());
        }
    }

    static void testJsonEmptyDescription() {
        section("Task - description vide");

        Task t = new Task(4, "Titre", "", LocalDate.of(2026, 12, 31), Task.Status.DONE);
        Task restored = Task.fromJson(t.toJson());
        assertEquals("description vide préservée", "", restored.getDescription());
    }

    static void testStatusParseIgnoresCase() {
        section("Task.Status - insensible à la casse");

        assertEquals("todo minuscule",  Task.Status.TODO,  Task.Status.parseStatus("todo"));
        assertEquals("Doing mixte",     Task.Status.DOING, Task.Status.parseStatus("Doing"));
        assertEquals("DONE majuscule",  Task.Status.DONE,  Task.Status.parseStatus("DONE"));
    }

    static void testStatusParseInvalid() {
        section("Task.Status - valeur invalide");

        try {
            Task.Status.parseStatus("INVALID");
            fail("statut invalide lève exception", "aucune exception levée");
        } catch (IllegalArgumentException e) {
            ok("statut invalide lève IllegalArgumentException");
        }
    }

    // Tests TaskManager CRUD
    
    static void testAddAndGet() throws IOException {
        section("TaskManager - ajout et récupération");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());

        assertTrue("liste vide au départ", tm.getAllTasks().isEmpty());

        Task t = tm.addTask("Tache A", "Desc A", LocalDate.of(2025, 6, 1), Task.Status.TODO);
        assertEquals("ID = 1",      1,        t.getId());
        assertEquals("titre",       "Tache A", t.getTitle());
        assertEquals("total = 1",   1,        tm.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    static void testFindById() throws IOException {
        section("TaskManager - recherche par ID");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm.addTask("B", "d", LocalDate.now(), Task.Status.TODO);

        Optional<Task> found = tm.findTaskById(1);
        assertTrue("trouve ID 1",        found.isPresent());
        assertEquals("bon titre",        "A", found.get().getTitle());

        Optional<Task> missing = tm.findTaskById(99);
        assertFalse("ID 99 absent",      missing.isPresent());

        Files.deleteIfExists(f);
    }

    static void testUpdate() throws IOException {
        section("TaskManager - modification");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("Original", "d", LocalDate.of(2025, 1, 1), Task.Status.TODO);

        boolean ok = tm.updateTask(1, "Modifie", null, null, Task.Status.DONE);
        assertTrue("updateTask retourne true", ok);

        Task updated = tm.findTaskById(1).get();
        assertEquals("titre modifié",  "Modifie",       updated.getTitle());
        assertEquals("statut modifié", Task.Status.DONE, updated.getStatus());
        assertEquals("date inchangée", LocalDate.of(2025, 1, 1), updated.getDueDate());

        assertFalse("ID inexistant retourne false", tm.updateTask(99, "X", null, null, null));

        Files.deleteIfExists(f);
    }

    static void testDelete() throws IOException {
        section("TaskManager - suppression");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm.addTask("B", "d", LocalDate.now(), Task.Status.TODO);

        assertTrue("supprime ID 1",             tm.deleteTask(1));
        assertEquals("total = 1 après delete",  1, tm.getTotalTaskCount());
        assertFalse("ID 1 plus trouvable",       tm.findTaskById(1).isPresent());
        assertFalse("delete ID inexistant",      tm.deleteTask(99));

        Files.deleteIfExists(f);
    }

    static void testFilterByStatus() throws IOException {
        section("TaskManager - filtre par statut");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm.addTask("B", "d", LocalDate.now(), Task.Status.DOING);
        tm.addTask("C", "d", LocalDate.now(), Task.Status.DONE);
        tm.addTask("D", "d", LocalDate.now(), Task.Status.TODO);

        assertEquals("2 TODO",  2, tm.getTasksByStatus(Task.Status.TODO).size());
        assertEquals("1 DOING", 1, tm.getTasksByStatus(Task.Status.DOING).size());
        assertEquals("1 DONE",  1, tm.getTasksByStatus(Task.Status.DONE).size());
        assertEquals("count TODO == size", tm.getTasksByStatus(Task.Status.TODO).size(),
                                           tm.countTasksByStatus(Task.Status.TODO));

        Files.deleteIfExists(f);
    }

    static void testIdAutoIncrement() throws IOException {
        section("TaskManager - auto-incrément des IDs");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        Task t1 = tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        Task t2 = tm.addTask("B", "d", LocalDate.now(), Task.Status.TODO);
        Task t3 = tm.addTask("C", "d", LocalDate.now(), Task.Status.TODO);

        assertTrue("IDs croissants", t1.getId() < t2.getId() && t2.getId() < t3.getId());

        Files.deleteIfExists(f);
    }

    // Tests de persistence (sauvegarde + rechargement)
    
    static void testPersistence() throws IOException {
        section("TaskManager - persistence sur disque");

        Path f = tmpFile();

        // Session 1 : on crée des tâches
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Persistante", "desc", LocalDate.of(2026, 3, 15), Task.Status.DOING);
        tm1.addTask("Persistante 2", "desc2", LocalDate.of(2026, 4, 1), Task.Status.DONE);

        // Session 2 : nouveau TaskManager sur le même fichier
        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("2 tâches rechargées",    2,           tm2.getTotalTaskCount());
        assertEquals("titre OK après reload",  "Persistante", tm2.findTaskById(1).get().getTitle());
        assertEquals("statut OK après reload", Task.Status.DOING, tm2.findTaskById(1).get().getStatus());

        Files.deleteIfExists(f);
    }

    static void testPersistenceWithSpecialChars() throws IOException {
        section("TaskManager - persistence avec caractères spéciaux");

        Path f = tmpFile();

        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Titre \"quoted\"", "Desc \\backslash", LocalDate.of(2025, 1, 1), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        Task reloaded = tm2.findTaskById(1).get();
        assertEquals("guillemets préservés",  "Titre \"quoted\"",   reloaded.getTitle());
        assertEquals("backslash préservé",    "Desc \\backslash",   reloaded.getDescription());

        Files.deleteIfExists(f);
    }

    static void testIdContinuesAfterReload() throws IOException {
        section("TaskManager - IDs ne repartent pas de 1 après reload");

        Path f = tmpFile();

        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm1.addTask("B", "d", LocalDate.now(), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        Task t3 = tm2.addTask("C", "d", LocalDate.now(), Task.Status.TODO);
        assertTrue("nouvel ID > 2 après reload", t3.getId() > 2);

        Files.deleteIfExists(f);
    }

    static void testAtomicWriteNoPollution() throws IOException {
        section("TaskManager - pas de fichier .tmp résiduel");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);

        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        assertFalse("pas de .tmp après sauvegarde", Files.exists(tmp));

        Files.deleteIfExists(f);
    }

    static void testEmptyFileHandled() throws IOException {
        section("TaskManager - fichier JSON vide");

        Path f = tmpFile();
        Files.writeString(f, "");

        TaskManager tm = new TaskManager(f.toString());
        assertEquals("0 tâches depuis fichier vide", 0, tm.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    static void testEmptyArrayHandled() throws IOException {
        section("TaskManager - fichier JSON []");

        Path f = tmpFile();
        Files.writeString(f, "[]");

        TaskManager tm = new TaskManager(f.toString());
        assertEquals("0 tâches depuis []", 0, tm.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    // Corner cases - caractères spéciaux dans les titres
    
    static void testApostropheInTitle() throws IOException {
        section("Corner - apostrophe dans le titre");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Configurer l'environnement", "desc", LocalDate.of(2025, 1, 1), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("apostrophe préservée", "Configurer l'environnement",
            tm2.findTaskById(1).get().getTitle());

        Files.deleteIfExists(f);
    }

    static void testColonInTitle() throws IOException {
        section("Corner - deux-points dans le titre");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Réunion 14:30 : ordre du jour", "desc", LocalDate.now(), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("deux-points préservés", "Réunion 14:30 : ordre du jour",
            tm2.findTaskById(1).get().getTitle());

        Files.deleteIfExists(f);
    }

    static void testCommaInDescription() throws IOException {
        section("Corner - virgule dans la description");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Titre", "étape 1, étape 2, étape 3", LocalDate.now(), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("virgules préservées", "étape 1, étape 2, étape 3",
            tm2.findTaskById(1).get().getDescription());

        Files.deleteIfExists(f);
    }

    static void testUnicodeInTitle() throws IOException {
        section("Corner - unicode / accents dans le titre");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Tâche avec éàü et 日本語", "desc", LocalDate.now(), Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("unicode préservé", "Tâche avec éàü et 日本語",
            tm2.findTaskById(1).get().getTitle());

        Files.deleteIfExists(f);
    }

    // Corner cases - affichage

    static void testTableRowTruncation() {
        section("Corner - troncature toTableRow");

        String longTitle = "A".repeat(40);
        String longDesc  = "B".repeat(50);
        Task t = new Task(1, longTitle, longDesc, LocalDate.now(), Task.Status.TODO);
        String row = t.toTableRow();

        // Le titre est tronqué à 30 chars (27 + "...")
        assertTrue("titre tronqué dans la ligne", row.contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAA..."));
        // La description est tronquée à 40 chars (37 + "...")
        assertTrue("description tronquée dans la ligne", row.contains("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB..."));
    }

    static void testTableRowExactLength() {
        section("Corner - toTableRow sans troncature si longueur exacte");

        String title30 = "T".repeat(30);
        String desc40  = "D".repeat(40);
        Task t = new Task(1, title30, desc40, LocalDate.now(), Task.Status.DOING);
        String row = t.toTableRow();

        assertTrue("titre exact non tronqué", row.contains(title30));
        assertTrue("desc exacte non tronquée", row.contains(desc40));
        assertFalse("pas de ... si longueur exacte", row.contains(title30 + "..."));
    }

    static void testDetailCardMultiDigitId() {
        section("Corner - toDetailCard avec ID multi-chiffres");

        Task t = new Task(1234, "Titre", "Desc", LocalDate.now(), Task.Status.DONE);
        String card = t.toDetailCard();
        assertTrue("ID 1234 présent dans la fiche", card.contains("1234"));
    }

    // Corner cases - dates limites
    
    static void testMinDate() throws IOException {
        section("Corner - date minimale (LocalDate.MIN)");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Min date", "desc", LocalDate.MIN, Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("LocalDate.MIN préservée", LocalDate.MIN,
            tm2.findTaskById(1).get().getDueDate());

        Files.deleteIfExists(f);
    }

    static void testMaxDate() throws IOException {
        section("Corner - date maximale (LocalDate.MAX)");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("Max date", "desc", LocalDate.MAX, Task.Status.TODO);

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("LocalDate.MAX préservée", LocalDate.MAX,
            tm2.findTaskById(1).get().getDueDate());

        Files.deleteIfExists(f);
    }

    static void testPastDate() throws IOException {
        section("Corner - date dans le passé acceptée");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        LocalDate past = LocalDate.of(1990, 1, 1);
        Task t = tm.addTask("Vieille tâche", "desc", past, Task.Status.DONE);

        assertEquals("date passée acceptée", past, t.getDueDate());

        Files.deleteIfExists(f);
    }

    // Corner cases - suppression et IDs
    
    static void testDeleteAllThenReload() throws IOException {
        section("Corner - supprimer toutes les tâches puis recharger");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm1.addTask("B", "d", LocalDate.now(), Task.Status.TODO);
        tm1.deleteTask(1);
        tm1.deleteTask(2);

        // Le fichier doit contenir [] et se recharger proprement
        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("0 tâches après suppression totale et reload", 0, tm2.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    static void testIdsNotReusedAfterDelete() throws IOException {
        section("Corner - IDs non réutilisés après suppression");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO); // ID 1
        tm.addTask("B", "d", LocalDate.now(), Task.Status.TODO); // ID 2
        tm.deleteTask(1);
        Task t3 = tm.addTask("C", "d", LocalDate.now(), Task.Status.TODO);

        assertTrue("nouvel ID > 2, pas de réutilisation", t3.getId() > 2);

        Files.deleteIfExists(f);
    }

    static void testDeleteAllThenAdd() throws IOException {
        section("Corner - supprimer tout puis ajouter une nouvelle tâche");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());
        tm1.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm1.deleteTask(1);

        TaskManager tm2 = new TaskManager(f.toString());
        Task t = tm2.addTask("Nouvelle", "d", LocalDate.now(), Task.Status.TODO);
        assertEquals("titre correct après reset", "Nouvelle", t.getTitle());
        assertEquals("1 tâche au total", 1, tm2.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    // Corner cases - update sans changement
    
    static void testUpdateAllNull() throws IOException {
        section("Corner - updateTask avec tous les champs null");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("Original", "desc originale", LocalDate.of(2025, 6, 1), Task.Status.TODO);

        boolean ok = tm.updateTask(1, null, null, null, null);
        assertTrue("updateTask retourne true", ok);

        Task t = tm.findTaskById(1).get();
        assertEquals("titre inchangé",       "Original",            t.getTitle());
        assertEquals("desc inchangée",       "desc originale",      t.getDescription());
        assertEquals("date inchangée",       LocalDate.of(2025, 6, 1), t.getDueDate());
        assertEquals("statut inchangé",      Task.Status.TODO,      t.getStatus());

        Files.deleteIfExists(f);
    }

    static void testUpdateOnlyStatus() throws IOException {
        section("Corner - updateTask sur le statut seulement");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("Titre", "desc", LocalDate.of(2025, 3, 1), Task.Status.TODO);

        tm.updateTask(1, null, null, null, Task.Status.DONE);

        Task t = tm.findTaskById(1).get();
        assertEquals("statut mis à jour", Task.Status.DONE, t.getStatus());
        assertEquals("titre inchangé",    "Titre",          t.getTitle());

        Files.deleteIfExists(f);
    }

    // Corner cases - volume
    
    static void testBulkAddAndReload() throws IOException {
        section("Corner - 100 tâches : sauvegarde et rechargement");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());

        for (int i = 1; i <= 100; i++) {
            Task.Status s = i % 3 == 0 ? Task.Status.DONE
                          : i % 3 == 1 ? Task.Status.TODO
                          : Task.Status.DOING;
            tm1.addTask("Tâche " + i, "Description " + i, LocalDate.now().plusDays(i), s);
        }

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("100 tâches rechargées", 100, tm2.getTotalTaskCount());

        // Vérifie quelques entrées au hasard
        assertEquals("tâche 1 OK",   "Tâche 1",   tm2.findTaskById(1).get().getTitle());
        assertEquals("tâche 50 OK",  "Tâche 50",  tm2.findTaskById(50).get().getTitle());
        assertEquals("tâche 100 OK", "Tâche 100", tm2.findTaskById(100).get().getTitle());

        // Vérifie les comptes par statut
        int todo  = (int) java.util.stream.IntStream.rangeClosed(1, 100).filter(i -> i % 3 == 1).count();
        int doing = (int) java.util.stream.IntStream.rangeClosed(1, 100).filter(i -> i % 3 == 2).count();
        int done  = (int) java.util.stream.IntStream.rangeClosed(1, 100).filter(i -> i % 3 == 0).count();

        assertEquals("count TODO correct",  todo,  tm2.countTasksByStatus(Task.Status.TODO));
        assertEquals("count DOING correct", doing, tm2.countTasksByStatus(Task.Status.DOING));
        assertEquals("count DONE correct",  done,  tm2.countTasksByStatus(Task.Status.DONE));

        Files.deleteIfExists(f);
    }

    static void testBulkDeleteAndReload() throws IOException {
        section("Corner - suppression en masse puis reload");

        Path f = tmpFile();
        TaskManager tm1 = new TaskManager(f.toString());

        for (int i = 1; i <= 20; i++) {
            tm1.addTask("T" + i, "d", LocalDate.now(), Task.Status.TODO);
        }
        // Supprimer les tâches paires
        for (int i = 2; i <= 20; i += 2) {
            tm1.deleteTask(i);
        }

        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("10 tâches après suppression paires", 10, tm2.getTotalTaskCount());

        // Vérifier que seules les impaires sont présentes
        for (int i = 1; i <= 20; i++) {
            if (i % 2 == 1) {
                assertTrue("tâche impaire " + i + " présente", tm2.findTaskById(i).isPresent());
            } else {
                assertFalse("tâche paire " + i + " absente", tm2.findTaskById(i).isPresent());
            }
        }

        Files.deleteIfExists(f);
    }

    // Corner cases - getAllTasks defensive copy
    
    static void testGetAllTasksIsDefensiveCopy() throws IOException {
        section("Corner - getAllTasks retourne une copie défensive");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());
        tm.addTask("A", "d", LocalDate.now(), Task.Status.TODO);
        tm.addTask("B", "d", LocalDate.now(), Task.Status.TODO);

        java.util.List<Task> copy = tm.getAllTasks();
        copy.clear(); // modifier la copie ne doit pas affecter le manager

        assertEquals("manager toujours 2 tâches après clear de la copie", 2, tm.getTotalTaskCount());

        Files.deleteIfExists(f);
    }

    // Corner cases - JSON produit valide après chaque opération

    static void testJsonFileValidAfterEachOperation() throws IOException {
        section("Corner - fichier JSON valide après add/update/delete enchaînés");

        Path f = tmpFile();
        TaskManager tm = new TaskManager(f.toString());

        tm.addTask("A", "d1", LocalDate.of(2025, 1, 1), Task.Status.TODO);
        tm.addTask("B", "d2", LocalDate.of(2025, 2, 1), Task.Status.DOING);
        tm.addTask("C", "d3", LocalDate.of(2025, 3, 1), Task.Status.DONE);
        tm.updateTask(2, "B modifié", null, null, Task.Status.DONE);
        tm.deleteTask(1);

        // Recharger et vérifier la cohérence
        TaskManager tm2 = new TaskManager(f.toString());
        assertEquals("2 tâches après opérations mixtes", 2, tm2.getTotalTaskCount());
        assertFalse("tâche 1 supprimée", tm2.findTaskById(1).isPresent());
        assertEquals("tâche 2 modifiée", "B modifié", tm2.findTaskById(2).get().getTitle());
        assertEquals("tâche 3 intacte",  "C",         tm2.findTaskById(3).get().getTitle());

        Files.deleteIfExists(f);
    }

    // Point d'entrée

    public static void main(String[] args) throws IOException {
        System.out.println("\n  ========================================");
        System.out.println("  TASKMANAGER - TESTS");
        System.out.println("  ========================================");

        // Task serialization
        testJsonRoundtrip();
        testJsonEscaping();
        testJsonAllStatuses();
        testJsonEmptyDescription();
        testStatusParseIgnoresCase();
        testStatusParseInvalid();

        // TaskManager CRUD
        testAddAndGet();
        testFindById();
        testUpdate();
        testDelete();
        testFilterByStatus();
        testIdAutoIncrement();

        // Persistence
        testPersistence();
        testPersistenceWithSpecialChars();
        testIdContinuesAfterReload();
        testAtomicWriteNoPollution();
        testEmptyFileHandled();
        testEmptyArrayHandled();

        // Corner cases - caractères spéciaux
        testApostropheInTitle();
        testColonInTitle();
        testCommaInDescription();
        testUnicodeInTitle();

        // Corner cases - affichage
        testTableRowTruncation();
        testTableRowExactLength();
        testDetailCardMultiDigitId();

        // Corner cases - dates
        testMinDate();
        testMaxDate();
        testPastDate();

        // Corner cases - suppression et IDs
        testDeleteAllThenReload();
        testIdsNotReusedAfterDelete();
        testDeleteAllThenAdd();

        // Corner cases - update partiel
        testUpdateAllNull();
        testUpdateOnlyStatus();

        // Corner cases - volume
        testBulkAddAndReload();
        testBulkDeleteAndReload();

        // Corner cases - intégrité
        testGetAllTasksIsDefensiveCopy();
        testJsonFileValidAfterEachOperation();

        // Résumé
        System.out.println("\n  ========================================");
        System.out.printf("  %d/%d tests passés%n", passed, passed + failed);
        System.out.println("  ========================================\n");

        if (failed > 0) System.exit(1);
    }
}