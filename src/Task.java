package taskmanager;

// Félix Vandenbroucke - LP Dev 2026
// Classe qui représente une tâche

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Modèle de données d'une tâche.
 * <p>
 * Gère sa propre sérialisation JSON (sans bibliothèque externe) et
 * fournit les représentations textuelles utilisées par la console et l'API.
 * </p>
 */
public class Task {

    private static final Logger LOG = Logger.getLogger(Task.class.getName());

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SAVE_DATE_FORMAT    = DateTimeFormatter.ISO_LOCAL_DATE;

    // Enums
    
    /** Les 3 états possibles d'une tâche. */
    public enum Status {
        TODO, DOING, DONE;

        /**
         * Convertit une chaîne en {@code Status} (insensible à la casse).
         *
         * @param text la valeur à parser
         * @return le statut correspondant
         * @throws IllegalArgumentException si la valeur est inconnue
         */
        public static Status parseStatus(String text) {
            return switch (text.toUpperCase().trim()) {
                case "TODO"  -> TODO;
                case "DOING" -> DOING;
                case "DONE"  -> DONE;
                default -> throw new IllegalArgumentException(
                    "Statut inconnu : \"" + text + "\". Valeurs acceptees : TODO, DOING, DONE.");
            };
        }
    }

    /** Niveau de priorité d'une tâche. */
    public enum Priority {
        LOW, MEDIUM, HIGH;

        /**
         * Convertit une chaîne en {@code Priority} (insensible à la casse).
         *
         * @param text la valeur à parser
         * @return la priorité correspondante
         * @throws IllegalArgumentException si la valeur est inconnue
         */
        public static Priority parsePriority(String text) {
            return switch (text.toUpperCase().trim()) {
                case "LOW"    -> LOW;
                case "MEDIUM" -> MEDIUM;
                case "HIGH"   -> HIGH;
                default -> throw new IllegalArgumentException(
                    "Priorite inconnue : \"" + text + "\". Valeurs acceptees : LOW, MEDIUM, HIGH.");
            };
        }
    }

    // Champs
    
    private final int      id;
    private String         title;
    private String         description;
    private LocalDate      dueDate;
    private Status         status;
    private Priority       priority;

    // Constructeurs
    
    /**
     * Crée une tâche avec tous ses champs.
     *
     * @param id          identifiant unique
     * @param title       titre (obligatoire)
     * @param description description libre (peut être vide)
     * @param dueDate     date d'échéance
     * @param status      statut courant
     * @param priority    niveau de priorité
     */
    public Task(int id, String title, String description,
                LocalDate dueDate, Status status, Priority priority) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.dueDate     = dueDate;
        this.status      = status;
        this.priority    = priority;
    }

    /**
     * Constructeur de compatibilité sans priorité (priorité MEDIUM par défaut).
     */
    public Task(int id, String title, String description, LocalDate dueDate, Status status) {
        this(id, title, description, dueDate, status, Priority.MEDIUM);
    }

    // Sérialisation JSON (sans bibliothèque externe)
    
    /**
     * Sérialise la tâche en JSON.
     * <p>
     * Format : {@code {"id":1,"title":"...","description":"...","dueDate":"2025-03-20","status":"TODO","priority":"MEDIUM"}}
     * </p>
     *
     * @return représentation JSON de la tâche
     */
    public String toJson() {
        return String.format(
            "{\"id\":%d,\"title\":\"%s\",\"description\":\"%s\",\"dueDate\":\"%s\",\"status\":\"%s\",\"priority\":\"%s\"}",
            id,
            escapeJsonString(title),
            escapeJsonString(description),
            dueDate.format(SAVE_DATE_FORMAT),
            status.name(),
            priority.name()
        );
    }

    /**
     * Recrée une {@code Task} depuis une ligne JSON produite par {@link #toJson()}.
     * <p>
     * Ne gère que le format interne — pas du JSON générique.
     * Les fichiers existants sans champ {@code priority} sont chargés avec MEDIUM par défaut.
     * </p>
     *
     * @param jsonLine ligne JSON à parser
     * @return la tâche reconstituée
     */
    public static Task fromJson(String jsonLine) {
        String content = jsonLine.trim().replaceAll("^\\{|\\}$", "");
        String[] fields = content.split(",(?=\")");

        int      id          = 0;
        String   title       = "";
        String   description = "";
        LocalDate dueDate    = LocalDate.now();
        Status   status      = Status.TODO;
        Priority priority    = Priority.MEDIUM;

        for (String field : fields) {
            String[] keyValue = field.split(":", 2);
            String key = keyValue[0].replace("\"", "").trim();
            String raw = keyValue[1].trim();
            String value = (raw.startsWith("\"") && raw.endsWith("\""))
                ? raw.substring(1, raw.length() - 1) : raw;

            switch (key) {
                case "id"          -> id          = Integer.parseInt(value);
                case "title"       -> title       = unescapeJsonString(value);
                case "description" -> description = unescapeJsonString(value);
                case "dueDate"     -> dueDate     = LocalDate.parse(value, SAVE_DATE_FORMAT);
                case "status"      -> status      = Status.parseStatus(value);
                case "priority"    -> {
                    try { priority = Priority.parsePriority(value); }
                    catch (IllegalArgumentException e) {
                        LOG.warning("Priorité inconnue ignorée : " + value);
                    }
                }
            }
        }

        return new Task(id, title, description, dueDate, status, priority);
    }

    // Représentations texte (console)
    
    /**
     * Retourne une ligne formatée pour l'affichage en tableau console.
     *
     * @return ligne tabulaire prête à afficher
     */
    public String toTableRow() {
        String statusLabel = switch (status) {
            case TODO  -> "[ TODO  ]";
            case DOING -> "[ DOING ]";
            case DONE  -> "[ DONE  ]";
        };
        String priorityLabel = switch (priority) {
            case LOW    -> "[LOW ]";
            case MEDIUM -> "[MED ]";
            case HIGH   -> "[HIGH]";
        };

        return String.format("%-4d %s %s  %-28s  %-38s  %s",
            id, statusLabel, priorityLabel,
            cropText(title, 28),
            cropText(description, 38),
            dueDate.format(DISPLAY_DATE_FORMAT)
        );
    }

    /**
     * Retourne une fiche détaillée multi-lignes pour l'affichage console.
     *
     * @return fiche encadrée prête à afficher
     */
    public String toDetailCard() {
        String line = "  +------------------------------------------+";
        return  line                                                        + "\n" +
                "  |  ID          : " + id                                  + "\n" +
                "  |  Titre       : " + title                               + "\n" +
                "  |  Description : " + description                         + "\n" +
                "  |  Echeance    : " + dueDate.format(DISPLAY_DATE_FORMAT) + "\n" +
                "  |  Statut      : " + status.name()                       + "\n" +
                "  |  Priorite    : " + priority.name()                     + "\n" +
                line;
    }

    /**
     * Retourne une ligne au format CSV.
     * <p>
     * Colonnes : {@code id,title,description,dueDate,status,priority}
     * </p>
     *
     * @return ligne CSV de la tâche
     */
    public String toCsvRow() {
        return String.join(",",
            String.valueOf(id),
            escapeCsv(title),
            escapeCsv(description),
            dueDate.format(SAVE_DATE_FORMAT),
            status.name(),
            priority.name()
        );
    }

    // Helpers privés
    
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

    private static String escapeCsv(String text) {
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String cropText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Getters / setters
    
    /** @return l'identifiant unique de la tâche */
    public int       getId()          { return id; }
    /** @return le titre */
    public String    getTitle()       { return title; }
    /** @return la description */
    public String    getDescription() { return description; }
    /** @return la date d'échéance */
    public LocalDate getDueDate()     { return dueDate; }
    /** @return le statut courant */
    public Status    getStatus()      { return status; }
    /** @return le niveau de priorité */
    public Priority  getPriority()    { return priority; }

    public void setTitle(String t)       { this.title       = t; }
    public void setDescription(String d) { this.description = d; }
    public void setDueDate(LocalDate d)  { this.dueDate     = d; }
    public void setStatus(Status s)      { this.status      = s; }
    public void setPriority(Priority p)  { this.priority    = p; }
}
