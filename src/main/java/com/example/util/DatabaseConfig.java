package com.example.util;

/** Constantes de connexion partagees par l'application et les tests. */
public final class DatabaseConfig {

    public static final String URL = "jdbc:h2:mem:reservation;DB_CLOSE_DELAY=-1";
    public static final String UTILISATEUR = "sa";
    public static final String MOT_DE_PASSE = "";

    public static final String SCRIPT_SCHEMA = "db/schema/v1_0_init.sql";
    public static final String SCRIPT_MIGRATION = "db/migration/v2_0_add_description.sql";

    public static final String UNITE_PERSISTANCE = "ReservationPU";

    private DatabaseConfig() {
    }
}
