package taskmanager;

// Lucas Mercier - LP Dev 2025
// Classe qui représente une tâche

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Task {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SAVE_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    // Les 3 états possibles d'une tâche
    public enum Status {
        TODO,
        DOING,
        DONE;

        // Convertit une String en Status (insensible à la casse)
        public static Status parseStatus(String text) {
            String normalized = text.toUpperCase().trim();

            if (normalized.equals("TODO"))  return TODO;
            if (normalized.equals("DOING")) return DOING;
            if (normalized.equals("DONE"))  return DONE;

            throw new IllegalArgumentException(
                "Statut inconnu : \"" + text + "\". Valeurs acceptees : TODO, DOING, DONE."
            );
        }
    }

    private final int    id;
    private String       title;
    private String       description;
    private LocalDate    dueDate;
    private Status       status;

    public Task(int id, String title, String description, LocalDate dueDate, Status status) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.dueDate     = dueDate;
        this.status      = status;
    }

    // Sérialisation JSON à la main (pas de lib externe)
    // Format : {"id":1,"title":"...","description":"...","dueDate":"2025-03-20","status":"TODO"}
    public String toJson() {
        return String.format(
            "{\"id\":%d,\"title\":\"%s\",\"description\":\"%s\",\"dueDate\":\"%s\",\"status\":\"%s\"}",
            id,
            escapeJsonString(title),
            escapeJsonString(description),
            dueDate.format(SAVE_DATE_FORMAT),
            status.name()
        );
    }

    // Recrée une Task depuis une ligne JSON produite par toJson()
    // Attention : ne gère que notre format, pas du JSON générique
    public static Task fromJson(String jsonLine) {
        String content = jsonLine.trim().replaceAll("^\\{|\\}$", "");

        // On coupe sur les virgules devant un " pour ne pas couper dans les valeurs
        String[] fields = content.split(",(?=\")");

        int       id          = 0;
        String    title       = "";
        String    description = "";
        LocalDate dueDate     = LocalDate.now();
        Status    status      = Status.TODO;

        for (String field : fields) {
            String[] keyValue = field.split(":", 2);
            String key   = keyValue[0].replace("\"", "").trim();
            String value = keyValue[1].replace("\"", "").trim();

            switch (key) {
                case "id"          -> id          = Integer.parseInt(value);
                case "title"       -> title       = unescapeJsonString(value);
                case "description" -> description = unescapeJsonString(value);
                case "dueDate"     -> dueDate     = LocalDate.parse(value, SAVE_DATE_FORMAT);
                case "status"      -> status      = Status.parseStatus(value);
            }
        }

        return new Task(id, title, description, dueDate, status);
    }

    // Ligne compacte pour le tableau (liste des tâches)
    public String toTableRow() {
        String statusLabel;
        switch (status) {
            case TODO  -> statusLabel = "[ TODO  ]";
            case DOING -> statusLabel = "[ DOING ]";
            case DONE  -> statusLabel = "[ DONE  ]";
            default    -> statusLabel = "[  ???  ]";
        }

        return String.format("%-4d %s  %-30s  %-40s  %s",
            id,
            statusLabel,
            cropText(title, 30),
            cropText(description, 40),
            dueDate.format(DISPLAY_DATE_FORMAT)
        );
    }

    // Fiche détaillée (affichée après création, avant suppression, etc.)
    public String toDetailCard() {
        String line = "  +------------------------------------------+";
        return  line                                                        + "\n" +
                "  |  ID          : " + id                                  + "\n" +
                "  |  Titre       : " + title                               + "\n" +
                "  |  Description : " + description                         + "\n" +
                "  |  Echeance    : " + dueDate.format(DISPLAY_DATE_FORMAT) + "\n" +
                "  |  Statut      : " + status.name()                       + "\n" +
                line;
    }

    // Échappe les caractères spéciaux pour le JSON
    // Important : traiter les \ en premier sinon on double-échappe
    private static String escapeJsonString(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }

    private static String unescapeJsonString(String text) {
        return text
            .replace("\\n",  "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    // Tronque le texte si trop long pour l'affichage tableau
    private static String cropText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    public int       getId()          { return id; }
    public String    getTitle()       { return title; }
    public String    getDescription() { return description; }
    public LocalDate getDueDate()     { return dueDate; }
    public Status    getStatus()      { return status; }

    public void setTitle(String newTitle)             { this.title       = newTitle; }
    public void setDescription(String newDescription) { this.description = newDescription; }
    public void setDueDate(LocalDate newDueDate)      { this.dueDate     = newDueDate; }
    public void setStatus(Status newStatus)           { this.status      = newStatus; }
}