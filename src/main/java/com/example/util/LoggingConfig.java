package com.example.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Journalisation java.util.logging : une ligne courte par evenement. */
public final class LoggingConfig {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private LoggingConfig() {
    }

    public static void installer() {
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                String niveau = String.format("%-5s", record.getLevel().getName());
                String source = record.getLoggerName();
                int point = source.lastIndexOf('.');
                if (point >= 0) {
                    source = source.substring(point + 1);
                }
                String message = formatMessage(record);
                if (record.getThrown() != null) {
                    message = message + " -> " + record.getThrown().getClass().getSimpleName();
                }
                return "[" + niveau + "] " + LocalDateTime.now().format(HEURE) + " " + source + " - " + message + "\n";
            }
        };

        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(formatter);
        console.setLevel(Level.INFO);
        console.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                return estAffiche(record.getLoggerName());
            }
        });
        root.addHandler(console);
        root.setLevel(Level.INFO);

        // Hibernate et les bibliotheques techniques restent discrets : leurs logs ne sont pas le sujet du TP
        Logger.getLogger("org.hibernate").setLevel(Level.WARNING);
        Logger.getLogger("org.jboss").setLevel(Level.WARNING);
        Logger.getLogger("com.zaxxer").setLevel(Level.WARNING);
    }

    /** Seuls les logs de l'application sont affiches : la sortie reste lisible pour le TP. */
    private static boolean estAffiche(String nomLogger) {
        return nomLogger != null && nomLogger.toLowerCase().startsWith("com.example");
    }
}
