package taskmanager;

// Félix Vandenbroucke - Dev 2026

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Gère tout l'affichage console et les saisies utilisateur.
 * Ne contient aucune logique métier, tout passe par TaskManager.
 */
public class ConsoleUI {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String MENU_SEPARATOR  = "-".repeat(60);
    private static final String TABLE_SEPARATOR = "-".repeat(95);

    private final TaskManager manager;
    private final Scanner inputScanner;

    public ConsoleUI(TaskManager manager) {
        this.manager      = manager;
        this.inputScanner = new Scanner(System.in);
    }

    public void run() {
        try (inputScanner) {
            printWelcomeBanner();

            boolean isRunning = true;
            while (isRunning) {
                printMainMenu();
                String userChoice = readLine("Votre choix").trim();
                isRunning = handleMenuChoice(userChoice);
            }

            System.out.println("\nAu revoir !");
        }
    }

    private boolean handleMenuChoice(String choice) {
        switch (choice) {
            case "1" -> runAddTaskFlow();
            case "2" -> runListTasksFlow();
            case "3" -> runEditTaskFlow();
            case "4" -> runDeleteTaskFlow();
            case "5" -> runShowStatsFlow();
            case "0" -> { return false; }
            default  -> System.out.println("  Choix invalide. Entrez un chiffre entre 0 et 5.\n");
        }
        return true;
    }

    private void runAddTaskFlow() {
        printSectionHeader("AJOUTER UNE TACHE");

        String title       = readRequiredLine("Titre");
        String description = readLine("Description (facultative, Entree pour ignorer)");
        LocalDate dueDate  = readDate("Date d'echeance (format : dd/MM/yyyy)");
        Task.Status status = readStatus("Statut (TODO / DOING / DONE) [defaut : TODO]", Task.Status.TODO);
        Task.Priority priority = readPriority("Priorite (LOW / MEDIUM / HIGH) [defaut : MEDIUM]", Task.Priority.MEDIUM);

        Task created = manager.addTask(title, description, dueDate, status, priority);

        System.out.println("\n  Tache ajoutee avec succes :");
        System.out.println(created.toDetailCard());
        System.out.println();
    }

    private void runListTasksFlow() {
        printSectionHeader("LISTE DES TACHES");

        System.out.println("  Filtrer par statut :");
        System.out.println("  [1] Toutes les taches");
        System.out.println("  [2] TODO seulement");
        System.out.println("  [3] DOING seulement");
        System.out.println("  [4] DONE seulement");

        String filterChoice = readLine("Filtre").trim();

        List<Task> tasksToDisplay;
        switch (filterChoice) {
            case "2" -> tasksToDisplay = manager.getTasksByStatus(Task.Status.TODO);
            case "3" -> tasksToDisplay = manager.getTasksByStatus(Task.Status.DOING);
            case "4" -> tasksToDisplay = manager.getTasksByStatus(Task.Status.DONE);
            default  -> tasksToDisplay = manager.getAllTasks();
        }

        if (tasksToDisplay.isEmpty()) {
            System.out.println("\n  Aucune tache a afficher.\n");
            return;
        }

        System.out.println("\n  " + TABLE_SEPARATOR);
        System.out.printf("  %-4s  %-10s  %-30s  %-40s  %s%n",
            "ID", "STATUT", "TITRE", "DESCRIPTION", "ECHEANCE");
        System.out.println("  " + TABLE_SEPARATOR);

        for (Task task : tasksToDisplay) {
            System.out.println("  " + task.toTableRow());
        }

        System.out.println("  " + TABLE_SEPARATOR);
        System.out.println("  " + tasksToDisplay.size() + " tache(s) affichee(s)\n");
    }

    private void runEditTaskFlow() {
        printSectionHeader("MODIFIER UNE TACHE");

        int taskId = readInt("ID de la tache a modifier");

        Optional<Task> searchResult = manager.findTaskById(taskId);
        if (searchResult.isEmpty()) {
            System.out.println("  Aucune tache trouvee avec l'ID " + taskId + ".\n");
            return;
        }

        Task existing = searchResult.get();
        System.out.println("\n  Tache actuelle :");
        System.out.println(existing.toDetailCard());
        System.out.println("\n  Laissez un champ vide pour le conserver tel quel.\n");

        String rawTitle  = readLine("Nouveau titre       [actuel : " + existing.getTitle() + "]");
        String rawDesc   = readLine("Nouvelle description [actuel : " + existing.getDescription() + "]");
        String rawDate   = readLine("Nouvelle echeance   [actuel : " + existing.getDueDate().format(INPUT_DATE_FORMAT) + "] (dd/MM/yyyy)");
        String rawStatus   = readLine("Nouveau statut      [actuel : " + existing.getStatus()   + "] (TODO/DOING/DONE)");
        String rawPriority = readLine("Nouvelle priorite   [actuel : " + existing.getPriority() + "] (LOW/MEDIUM/HIGH)");

        String        newTitle    = rawTitle.isBlank()    ? null : rawTitle.trim();
        String        newDesc     = rawDesc.isBlank()     ? null : rawDesc.trim();
        LocalDate     newDate     = null;
        Task.Status   newStatus   = null;
        Task.Priority newPriority = null;

        if (!rawDate.isBlank()) {
            try {
                newDate = LocalDate.parse(rawDate.trim(), INPUT_DATE_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("  Date invalide, ce champ sera ignore.");
            }
        }

        if (!rawStatus.isBlank()) {
            try {
                newStatus = Task.Status.parseStatus(rawStatus.trim());
            } catch (IllegalArgumentException e) {
                System.out.println("  Statut invalide, ce champ sera ignore.");
            }
        }

        if (!rawPriority.isBlank()) {
            try {
                newPriority = Task.Priority.parsePriority(rawPriority.trim());
            } catch (IllegalArgumentException e) {
                System.out.println("  Priorite invalide, ce champ sera ignore.");
            }
        }

        boolean ok = manager.updateTask(taskId, newTitle, newDesc, newDate, newStatus, newPriority);
        if (ok) {
            System.out.println("\n  Tache #" + taskId + " mise a jour.\n");
        } else {
            System.out.println("\n  Echec de la mise a jour.\n");
        }
    }

    private void runDeleteTaskFlow() {
        printSectionHeader("SUPPRIMER UNE TACHE");

        int taskId = readInt("ID de la tache a supprimer");

        Optional<Task> searchResult = manager.findTaskById(taskId);
        if (searchResult.isEmpty()) {
            System.out.println("  Aucune tache trouvee avec l'ID " + taskId + ".\n");
            return;
        }

        System.out.println("\n  Tache a supprimer :");
        System.out.println(searchResult.get().toDetailCard());

        String confirmation = readLine("\n  Etes-vous sur ? Tapez 'oui' pour confirmer").trim();

        if (confirmation.equalsIgnoreCase("oui")) {
            manager.deleteTask(taskId);
            System.out.println("  Tache #" + taskId + " supprimee.\n");
        } else {
            System.out.println("  Suppression annulee.\n");
        }
    }

    private void runShowStatsFlow() {
        printSectionHeader("STATISTIQUES");

        int countTodo  = manager.countTasksByStatus(Task.Status.TODO);
        int countDoing = manager.countTasksByStatus(Task.Status.DOING);
        int countDone  = manager.countTasksByStatus(Task.Status.DONE);
        int total      = manager.getTotalTaskCount();

        System.out.printf("  %-10s : %d tache(s)%n", "TODO",  countTodo);
        System.out.printf("  %-10s : %d tache(s)%n", "DOING", countDoing);
        System.out.printf("  %-10s : %d tache(s)%n", "DONE",  countDone);
        System.out.println("  " + "-".repeat(25));
        System.out.printf("  %-10s : %d tache(s)%n%n", "Total", total);
    }

    private void printWelcomeBanner() {
        System.out.println();
        System.out.println("  +==============================================+");
        System.out.println("  |       GESTIONNAIRE DE TACHES  v1.0           |");
        System.out.println("  +==============================================+");
        System.out.println();
    }

    private void printMainMenu() {
        System.out.println("  " + MENU_SEPARATOR);
        System.out.println("  MENU PRINCIPAL");
        System.out.println("  " + MENU_SEPARATOR);
        System.out.println("  [1] Ajouter une tache");
        System.out.println("  [2] Lister les taches");
        System.out.println("  [3] Modifier une tache");
        System.out.println("  [4] Supprimer une tache");
        System.out.println("  [5] Statistiques");
        System.out.println("  [0] Quitter");
        System.out.println("  " + MENU_SEPARATOR);
    }

    private void printSectionHeader(String title) {
        System.out.println("\n  --- " + title + " ---\n");
    }

    private String readLine(String prompt) {
        System.out.print("  > " + prompt + " : ");
        return inputScanner.nextLine();
    }

    // Redemande tant que c'est vide (pour les champs obligatoires)
    private String readRequiredLine(String prompt) {
        String value = "";
        while (value.isBlank()) {
            value = readLine(prompt).trim();
            if (value.isBlank()) {
                System.out.println("  Ce champ est obligatoire.");
            }
        }
        return value;
    }

    private int readInt(String prompt) {
        while (true) {
            String raw = readLine(prompt).trim();
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                System.out.println("  Veuillez entrer un nombre entier.");
            }
        }
    }

    // Entrée vide = demain par défaut
    private LocalDate readDate(String prompt) {
        while (true) {
            String raw = readLine(prompt).trim();

            if (raw.isEmpty()) {
                LocalDate defaultDate = LocalDate.now().plusDays(1);
                System.out.println("  Date par defaut : " + defaultDate.format(INPUT_DATE_FORMAT));
                return defaultDate;
            }

            try {
                return LocalDate.parse(raw, INPUT_DATE_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("  Format invalide. Exemple : 25/03/2025");
            }
        }
    }

    private Task.Status readStatus(String prompt, Task.Status defaultStatus) {
        while (true) {
            String raw = readLine(prompt).trim();

            if (raw.isEmpty()) {
                return defaultStatus;
            }

            try {
                return Task.Status.parseStatus(raw);
            } catch (IllegalArgumentException e) {
                System.out.println("  Statut invalide. Entrez TODO, DOING ou DONE.");
            }
        }
    }

    private Task.Priority readPriority(String prompt, Task.Priority defaultPriority) {
        while (true) {
            String raw = readLine(prompt).trim();

            if (raw.isEmpty()) {
                return defaultPriority;
            }

            try {
                return Task.Priority.parsePriority(raw);
            } catch (IllegalArgumentException e) {
                System.out.println("  Priorite invalide. Entrez LOW, MEDIUM ou HIGH.");
            }
        }
    }
}