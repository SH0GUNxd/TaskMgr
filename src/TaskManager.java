package taskmanager;

// Félix Vandenbroucke - LP Dev 2026
// Gère la liste des tâches + lecture/écriture du fichier JSON

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Gère la liste des tâches en mémoire et sa synchronisation avec le fichier JSON.
 * <p>
 * Toutes les opérations de modification déclenchent une sauvegarde atomique immédiate :
 * les données sont écrites dans un fichier temporaire avant d'être déplacées sur le
 * fichier final, ce qui évite toute corruption en cas de coupure.
 * </p>
 */
public class TaskManager {

    private static final Logger LOG = Logger.getLogger(TaskManager.class.getName());

    private final Path       dataFilePath;
    private final List<Task> taskList;

    /** Compteur d'ID auto-incrémenté, remis à jour au chargement. */
    private int nextAvailableId;

    /**
     * Crée un {@code TaskManager} lié au fichier de sauvegarde spécifié.
     * Les tâches existantes sont chargées immédiatement.
     *
     * @param filePath chemin vers le fichier JSON de sauvegarde
     */
    public TaskManager(String filePath) {
        this.dataFilePath    = Paths.get(filePath);
        this.taskList        = new ArrayList<>();
        this.nextAvailableId = 1;
        loadTasksFromFile();
    }

    // CRUD
    
    /**
     * Crée une nouvelle tâche et la sauvegarde immédiatement.
     *
     * @param title       titre (obligatoire)
     * @param description description libre
     * @param dueDate     date d'échéance
     * @param status      statut initial
     * @param priority    niveau de priorité
     * @return la tâche créée avec son ID assigné
     */
    public Task addTask(String title, String description, LocalDate dueDate,
                        Task.Status status, Task.Priority priority) {
        Task newTask = new Task(nextAvailableId, title, description, dueDate, status, priority);
        nextAvailableId++;
        taskList.add(newTask);
        saveTasksToFile();
        LOG.info("Tâche créée : #" + newTask.getId() + " \"" + title + "\"");
        return newTask;
    }

    /**
     * Crée une nouvelle tâche avec priorité MEDIUM par défaut.
     *
     * @param title       titre (obligatoire)
     * @param description description libre
     * @param dueDate     date d'échéance
     * @param status      statut initial
     * @return la tâche créée avec son ID assigné
     */
    public Task addTask(String title, String description, LocalDate dueDate, Task.Status status) {
        return addTask(title, description, dueDate, status, Task.Priority.MEDIUM);
    }

    /**
     * Retourne une copie défensive de la liste complète des tâches.
     *
     * @return liste de toutes les tâches
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(taskList);
    }

    /**
     * Retourne les tâches filtrées par statut.
     *
     * @param statusFilter le statut à filtrer
     * @return liste des tâches correspondantes
     */
    public List<Task> getTasksByStatus(Task.Status statusFilter) {
        List<Task> result = new ArrayList<>();
        for (Task task : taskList) {
            if (task.getStatus() == statusFilter) result.add(task);
        }
        return result;
    }

    /**
     * Recherche une tâche par son identifiant.
     *
     * @param id l'identifiant à rechercher
     * @return un {@code Optional} contenant la tâche, ou vide si introuvable
     */
    public Optional<Task> findTaskById(int id) {
        for (Task task : taskList) {
            if (task.getId() == id) return Optional.of(task);
        }
        return Optional.empty();
    }

    /**
     * Met à jour une tâche existante. Les paramètres {@code null} laissent le champ inchangé.
     *
     * @param id             identifiant de la tâche à modifier
     * @param newTitle       nouveau titre, ou {@code null} pour conserver l'existant
     * @param newDescription nouvelle description, ou {@code null} pour conserver l'existante
     * @param newDueDate     nouvelle date, ou {@code null} pour conserver l'existante
     * @param newStatus      nouveau statut, ou {@code null} pour conserver l'existant
     * @param newPriority    nouvelle priorité, ou {@code null} pour conserver l'existante
     * @return {@code true} si la mise à jour a réussi, {@code false} si l'ID est introuvable
     */
    public boolean updateTask(int id, String newTitle, String newDescription,
                              LocalDate newDueDate, Task.Status newStatus, Task.Priority newPriority) {
        Optional<Task> result = findTaskById(id);
        if (result.isEmpty()) {
            LOG.warning("updateTask : tâche #" + id + " introuvable");
            return false;
        }

        Task t = result.get();
        if (newTitle       != null) t.setTitle(newTitle);
        if (newDescription != null) t.setDescription(newDescription);
        if (newDueDate     != null) t.setDueDate(newDueDate);
        if (newStatus      != null) t.setStatus(newStatus);
        if (newPriority    != null) t.setPriority(newPriority);

        saveTasksToFile();
        LOG.info("Tâche mise à jour : #" + id);
        return true;
    }

    /**
     * Met à jour une tâche sans modifier la priorité.
     *
     * @param id             identifiant de la tâche à modifier
     * @param newTitle       nouveau titre, ou {@code null} pour conserver l'existant
     * @param newDescription nouvelle description, ou {@code null} pour conserver l'existante
     * @param newDueDate     nouvelle date, ou {@code null} pour conserver l'existante
     * @param newStatus      nouveau statut, ou {@code null} pour conserver l'existant
     * @return {@code true} si la mise à jour a réussi, {@code false} si l'ID est introuvable
     */
    public boolean updateTask(int id, String newTitle, String newDescription,
                              LocalDate newDueDate, Task.Status newStatus) {
        return updateTask(id, newTitle, newDescription, newDueDate, newStatus, null);
    }

    /**
     * Supprime une tâche par son identifiant et sauvegarde immédiatement.
     *
     * @param id l'identifiant de la tâche à supprimer
     * @return {@code true} si une tâche a été supprimée, {@code false} sinon
     */
    public boolean deleteTask(int id) {
        boolean removed = taskList.removeIf(task -> task.getId() == id);
        if (removed) {
            saveTasksToFile();
            LOG.info("Tâche supprimée : #" + id);
        } else {
            LOG.warning("deleteTask : tâche #" + id + " introuvable");
        }
        return removed;
    }

    // Statistiques
    
    /**
     * Compte les tâches ayant un statut donné.
     *
     * @param status le statut à compter
     * @return le nombre de tâches correspondantes
     */
    public int countTasksByStatus(Task.Status status) {
        int count = 0;
        for (Task task : taskList) {
            if (task.getStatus() == status) count++;
        }
        return count;
    }

    /**
     * Retourne le nombre total de tâches.
     *
     * @return nombre total de tâches en mémoire
     */
    public int getTotalTaskCount() {
        return taskList.size();
    }

    /**
     * Génère l'export CSV de toutes les tâches.
     * <p>
     * La première ligne est l'en-tête : {@code id,title,description,dueDate,status,priority}
     * </p>
     *
     * @return contenu CSV complet
     */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder("id,title,description,dueDate,status,priority\n");
        for (Task t : taskList) {
            sb.append(t.toCsvRow()).append("\n");
        }
        return sb.toString();
    }

    // Persistence
    
    /**
     * Sauvegarde toute la liste dans le fichier JSON de manière atomique.
     * <p>
     * L'écriture se fait d'abord dans un fichier temporaire (suffixe {@code .tmp}),
     * puis un déplacement atomique remplace le fichier final.
     * En cas d'échec, le fichier original reste intact.
     * </p>
     */
    private void saveTasksToFile() {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < taskList.size(); i++) {
            sb.append("  ").append(taskList.get(i).toJson());
            if (i < taskList.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");

        Path tempFile = dataFilePath.resolveSibling(dataFilePath.getFileName() + ".tmp");
        try {
            Files.writeString(tempFile, sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, dataFilePath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.severe("Sauvegarde impossible : " + e.getMessage());
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
        }
    }

    /**
     * Charge les tâches depuis le fichier JSON au démarrage.
     * Si le fichier n'existe pas, démarre avec une liste vide.
     */
    private void loadTasksFromFile() {
        if (!Files.exists(dataFilePath)) {
            LOG.info("Pas de fichier de sauvegarde, on repart de zéro.");
            System.out.println("[INFO] Pas de fichier de sauvegarde, on repart de zero.");
            return;
        }

        try {
            String content = Files.readString(dataFilePath).trim();
            if (content.isEmpty() || content.equals("[]")) return;

            String inner = content.replaceAll("^\\[\\s*|\\s*]$", "").trim();
            if (inner.isEmpty()) return;

            String[] entries = inner.split("\\},\\s*\\{");
            for (String entry : entries) {
                if (!entry.startsWith("{")) entry = "{" + entry;
                if (!entry.endsWith("}"))   entry = entry + "}";
                try {
                    Task loaded = Task.fromJson(entry);
                    taskList.add(loaded);
                    if (loaded.getId() >= nextAvailableId) {
                        nextAvailableId = loaded.getId() + 1;
                    }
                } catch (Exception e) {
                    LOG.warning("Tâche ignorée (parsing) : " + e.getMessage());
                    System.err.println("[AVERTISSEMENT] Tache ignoree (parsing) : " + e.getMessage());
                }
            }

            LOG.info(taskList.size() + " tâche(s) chargée(s) depuis " + dataFilePath.getFileName());
            System.out.println("[INFO] " + taskList.size() + " tache(s) chargee(s) depuis " + dataFilePath.getFileName());

        } catch (IOException e) {
            LOG.severe("Lecture du fichier impossible : " + e.getMessage());
            System.err.println("[ERREUR] Lecture du fichier impossible : " + e.getMessage());
        }
    }
}
