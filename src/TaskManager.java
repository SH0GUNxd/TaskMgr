package taskmanager;

// Félix Vandenbroucke - Dev 2026
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

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
    
    public Task addTask(String title, String description, LocalDate dueDate,
                        Task.Status status, Task.Priority priority) {
        lock.writeLock().lock();
        try {
            Task newTask = new Task(nextAvailableId, title, description, dueDate, status, priority);
            nextAvailableId++;
            taskList.add(newTask);
            saveTasksToFile();
            LOG.info("Tâche créée : #" + newTask.getId() + " \"" + title + "\"");
            return newTask;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Task addTask(String title, String description, LocalDate dueDate, Task.Status status) {
        return addTask(title, description, dueDate, status, Task.Priority.MEDIUM);
    }

    public List<Task> getAllTasks() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(taskList);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Task> getTasksByStatus(Task.Status statusFilter) {
        lock.readLock().lock();
        try {
            List<Task> result = new ArrayList<>();
            for (Task task : taskList) {
                if (task.getStatus() == statusFilter) result.add(task);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Task> findTaskById(int id) {
        lock.readLock().lock();
        try {
            for (Task task : taskList) {
                if (task.getId() == id) return Optional.of(task);
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean updateTask(int id, String newTitle, String newDescription,
                              LocalDate newDueDate, Task.Status newStatus, Task.Priority newPriority) {
        lock.writeLock().lock();
        try {
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
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean updateTask(int id, String newTitle, String newDescription,
                              LocalDate newDueDate, Task.Status newStatus) {
        return updateTask(id, newTitle, newDescription, newDueDate, newStatus, null);
    }

    public boolean deleteTask(int id) {
        lock.writeLock().lock();
        try {
            boolean removed = taskList.removeIf(task -> task.getId() == id);
            if (removed) {
                saveTasksToFile();
                LOG.info("Tâche supprimée : #" + id);
            } else {
                LOG.warning("deleteTask : tâche #" + id + " introuvable");
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Statistiques
    
    public int countTasksByStatus(Task.Status status) {
        lock.readLock().lock();
        try {
            int count = 0;
            for (Task task : taskList) {
                if (task.getStatus() == status) count++;
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalTaskCount() {
        lock.readLock().lock();
        try {
            return taskList.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public String exportCsv() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder("id,title,description,dueDate,status,priority\n");
            for (Task t : taskList) {
                sb.append(t.toCsvRow()).append("\n");
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Persistence
    
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