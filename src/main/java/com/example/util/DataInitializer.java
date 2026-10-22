package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Jeu de donnees realiste : utilisateurs, salles et reservations a venir,
 * avec quelques creneaux qui se suivent dans la meme journee.
 */
public class DataInitializer {

    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private final EntityManager em;

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            List<Utilisateur> utilisateurs = Arrays.asList(
                    new Utilisateur("Alice Dupont", "alice.dupont@example.com"),
                    new Utilisateur("Bruno Martin", "bruno.martin@example.com"),
                    new Utilisateur("Chloe Bernard", "chloe.bernard@example.com"),
                    new Utilisateur("David Petit", "david.petit@example.com"),
                    new Utilisateur("Emma Robert", "emma.robert@example.com"),
                    new Utilisateur("Farid Haddad", "farid.haddad@example.com"));
            utilisateurs.forEach(em::persist);

            List<Salle> salles = Arrays.asList(
                    new Salle("Salle A", 20),
                    new Salle("Salle B", 12),
                    new Salle("Salle C", 8),
                    new Salle("Amphitheatre", 120),
                    new Salle("Salle Reunion", 6));
            salles.forEach(em::persist);

            LocalDate demain = LocalDate.now().plusDays(1);
            List<Reservation> reservations = new ArrayList<>();
            reservations.add(new Reservation(utilisateurs.get(0), salles.get(0), demain.atTime(LocalTime.of(9, 0)), demain.atTime(LocalTime.of(11, 0))));
            reservations.add(new Reservation(utilisateurs.get(1), salles.get(0), demain.atTime(LocalTime.of(11, 0)), demain.atTime(LocalTime.of(12, 30))));
            reservations.add(new Reservation(utilisateurs.get(2), salles.get(1), demain.atTime(LocalTime.of(14, 0)), demain.atTime(LocalTime.of(16, 0))));
            reservations.add(new Reservation(utilisateurs.get(3), salles.get(3), demain.atTime(LocalTime.of(8, 30)), demain.atTime(LocalTime.of(10, 0))));
            reservations.add(new Reservation(utilisateurs.get(4), salles.get(2), demain.atTime(LocalTime.of(10, 0)), demain.atTime(LocalTime.of(11, 0))));
            reservations.add(new Reservation(utilisateurs.get(5), salles.get(4), demain.atTime(LocalTime.of(15, 0)), demain.atTime(LocalTime.of(17, 0))));

            LocalDate apresDemain = demain.plusDays(1);
            reservations.add(new Reservation(utilisateurs.get(1), salles.get(0), apresDemain.atTime(LocalTime.of(9, 0)), apresDemain.atTime(LocalTime.of(12, 0))));
            reservations.add(new Reservation(utilisateurs.get(2), salles.get(3), apresDemain.atTime(LocalTime.of(13, 30)), apresDemain.atTime(LocalTime.of(15, 0))));
            reservations.add(new Reservation(utilisateurs.get(3), salles.get(1), apresDemain.atTime(LocalTime.of(9, 30)), apresDemain.atTime(LocalTime.of(10, 30))));
            reservations.add(new Reservation(utilisateurs.get(0), salles.get(2), apresDemain.atTime(LocalTime.of(16, 0)), apresDemain.atTime(LocalTime.of(18, 0))));
            reservations.forEach(em::persist);

            tx.commit();
            LOGGER.info(() -> "Jeu de donnees cree : " + utilisateurs.size() + " utilisateurs, "
                    + salles.size() + " salles, " + reservations.size() + " reservations");
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Echec de l'initialisation des donnees : " + e.getMessage());
            throw e;
        }
    }

    /** Complete la colonne description ajoutee par la migration v2.0. */
    public int renseignerDescriptions() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            List<Salle> salles = em.createQuery("SELECT s FROM Salle s ORDER BY s.nom", Salle.class).getResultList();
            int modifiees = 0;
            for (Salle salle : salles) {
                if (salle.getDescription() == null) {
                    salle.setDescription("Salle " + salle.getNom() + " - " + salle.getCapacite() + " places");
                    modifiees++;
                }
            }
            final int total = modifiees;
            tx.commit();
            LOGGER.info(() -> total + " description(s) de salle renseignee(s)");
            return modifiees;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
}
