package taskmanager;

// Lucas Mercier - LP Dev 2025
// Gère la liste des tâches + lecture/écriture du fichier JSON

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Contient toutes les opérations sur les tâches (ajout, modif, suppression, lecture).
 * S'occupe aussi de la sauvegarde automatique dans tasks.json.
 * ConsoleUI passe par cette classe pour tout, elle ne touche jamais la liste directement.
 */
public class TaskManager {

    private final Path dataFilePath;
    private final List<Task> taskList;

    // Compteur d'ID auto-incrémenté, remis à jour au chargement
    private int nextAvailableId;

    public TaskManager(String filePath) {
        this.dataFilePath    = Paths.get(filePath);
        this.taskList        = new ArrayList<>();
        this.nextAvailableId = 1;

        loadTasksFromFile();
    }

    /**
     * Ajoute une nouvelle tâche et sauvegarde immédiatement.
     */
    public Task addTask(String title, String description, LocalDate dueDate, Task.Status status) {
        Task newTask = new Task(nextAvailableId, title, description, dueDate, status);
        nextAvailableId++;
        taskList.add(newTask);
        saveTasksToFile();
        return newTask;
    }

    // Retourne une copie de la liste pour éviter les modifications extérieures
    public List<Task> getAllTasks() {
        return new ArrayList<>(taskList);
    }

    public List<Task> getTasksByStatus(Task.Status statusFilter) {
        List<Task> result = new ArrayList<>();
        for (Task task : taskList) {
            if (task.getStatus() == statusFilter) {
                result.add(task);
            }
        }
        return result;
    }

    public Optional<Task> findTaskById(int id) {
        for (Task task : taskList) {
            if (task.getId() == id) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    /**
     * Met à jour une tâche existante.
     * Les paramètres null = on ne touche pas au champ.
     * Retourne false si l'ID n'existe pas.
     */
    public boolean updateTask(int id, String newTitle, String newDescription,
                              LocalDate newDueDate, Task.Status newStatus) {
        Optional<Task> result = findTaskById(id);
        if (result.isEmpty()) {
            return false;
        }

        Task t = result.get();

        if (newTitle       != null) t.setTitle(newTitle);
        if (newDescription != null) t.setDescription(newDescription);
        if (newDueDate     != null) t.setDueDate(newDueDate);
        if (newStatus      != null) t.setStatus(newStatus);

        saveTasksToFile();
        return true;
    }

    public boolean deleteTask(int id) {
        boolean wasRemoved = taskList.removeIf(task -> task.getId() == id);
        if (wasRemoved) {
            saveTasksToFile();
        }
        return wasRemoved;
    }

    public int countTasksByStatus(Task.Status status) {
        int count = 0;
        for (Task task : taskList) {
            if (task.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    public int getTotalTaskCount() {
        return taskList.size();
    }

    // Écrit toute la liste dans le fichier JSON
    // Appelé après chaque modification pour ne rien perdre
    private void saveTasksToFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < taskList.size(); i++) {
            sb.append("  ").append(taskList.get(i).toJson());
            if (i < taskList.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("]");

        try {
            Files.writeString(
                dataFilePath,
                sb.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.err.println("[ERREUR] Sauvegarde impossible : " + e.getMessage());
        }
    }

    // Charge les tâches depuis le fichier au démarrage
    // Si le fichier n'existe pas encore c'est normal (premier lancement)
    private void loadTasksFromFile() {
        if (!Files.exists(dataFilePath)) {
            System.out.println("[INFO] Pas de fichier de sauvegarde, on repart de zero.");
            return;
        }

        try {
            String content = Files.readString(dataFilePath).trim();

            if (content.isEmpty() || content.equals("[]")) {
                return;
            }

            // On retire les crochets du tableau JSON
            String inner = content.replaceAll("^\\[\\s*|\\s*]$", "").trim();
            if (inner.isEmpty()) return;

            // Découpage sur },{ pour isoler chaque objet
            String[] entries = inner.split("\\},\\s*\\{");

            for (String entry : entries) {
                if (!entry.startsWith("{")) entry = "{" + entry;
                if (!entry.endsWith("}"))   entry = entry + "}";

                try {
                    Task loaded = Task.fromJson(entry);
                    taskList.add(loaded);

                    // Met à jour le compteur pour éviter les ID en double
                    if (loaded.getId() >= nextAvailableId) {
                        nextAvailableId = loaded.getId() + 1;
                    }
                } catch (Exception e) {
                    System.err.println("[AVERTISSEMENT] Tache ignoree (parsing) : " + e.getMessage());
                }
            }

            System.out.println("[INFO] " + taskList.size() + " tache(s) chargee(s) depuis " + dataFilePath.getFileName());

        } catch (IOException e) {
            System.err.println("[ERREUR] Lecture du fichier impossible : " + e.getMessage());
        }
    }
}