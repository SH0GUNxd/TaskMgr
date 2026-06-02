package taskmanager;

// Félix Vandenbroucke - Dev 2026
// Point d'entrée de l'application
//
// Modes de lancement :
//   java -cp out taskmanager.Main                  → mode console
//   java -cp out taskmanager.Main --web            → mode web (port 8080)
//   java -cp out taskmanager.Main --web 3000       → mode web (port custom)
//   java -cp out taskmanager.Main tasks.json       → console avec fichier custom

import java.io.IOException;

public class Main {

    private static final String DEFAULT_SAVE_FILE = "tasks.json";
    private static final int    DEFAULT_PORT      = 8080;
    private static final String DEFAULT_WEB_ROOT  = "web";

    public static void main(String[] args) throws IOException {
        boolean webMode  = false;
        int     port     = DEFAULT_PORT;
        String  saveFile = DEFAULT_SAVE_FILE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--web" -> {
                    webMode = true;
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        port = Integer.parseInt(args[++i]);
                    }
                }
                default -> saveFile = args[i];
            }
        }

        TaskManager manager = new TaskManager(saveFile);

        if (webMode) {
            ApiServer server = new ApiServer(manager, port, DEFAULT_WEB_ROOT);
            server.start();
            System.out.println("[INFO] Ouvrez http://localhost:" + port + " dans votre navigateur");
            System.out.println("[INFO] Ctrl+C pour arrêter");
        } else {
            ConsoleUI ui = new ConsoleUI(manager);
            ui.run();
        }
    }
}
