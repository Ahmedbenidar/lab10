package com.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Execute un script SQL du classpath sur la base via JDBC.
 * Chaque instruction est jouee separement pour rester compatible avec tous les SGBD.
 */
public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeMigration(String scriptPath) throws SQLException, IOException {
        List<String> instructions = lireInstructions(scriptPath);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {
            for (String instruction : instructions) {
                stmt.execute(instruction);
            }
        }
        LOGGER.info(() -> "Migration appliquee : " + scriptPath + " (" + instructions.size() + " instruction(s))");
    }

    /** Colonnes d'une table, dans l'ordre du catalogue JDBC. */
    public List<String> colonnes(String table) throws SQLException {
        List<String> colonnes = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, table.toUpperCase(), null)) {
                while (rs.next()) {
                    colonnes.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return colonnes;
    }

    public boolean tableExiste(String table) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, table.toUpperCase(), new String[] {"TABLE"})) {
                return rs.next();
            }
        }
    }

    static List<String> lireInstructions(String scriptPath) throws IOException {
        InputStream is = MigrationExecutor.class.getClassLoader().getResourceAsStream(scriptPath);
        if (is == null) {
            throw new IOException("Script introuvable dans le classpath : " + scriptPath);
        }
        StringBuilder contenu = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                if (ligne.trim().startsWith("--")) {
                    continue;
                }
                contenu.append(ligne).append('\n');
            }
        }
        List<String> instructions = new ArrayList<>();
        for (String brut : contenu.toString().split(";")) {
            String instruction = brut.trim();
            if (!instruction.isEmpty()) {
                instructions.add(instruction);
            }
        }
        return instructions;
    }
}
