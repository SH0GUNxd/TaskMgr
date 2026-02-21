package taskmanager;

// Lucas Mercier - LP Dev 2025
// Point d'entrée de l'application

public class Main {

    private static final String DEFAULT_SAVE_FILE = "tasks.json";

    public static void main(String[] args) {
        // On peut passer un autre fichier en argument si besoin
        String saveFile = (args.length > 0) ? args[0] : DEFAULT_SAVE_FILE;

        TaskManager manager = new TaskManager(saveFile);
        ConsoleUI ui = new ConsoleUI(manager);
        ui.run();
    }
}