package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.service.ConflitReservationException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.DatabaseConfig;
import com.example.util.LoggingConfig;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import javax.persistence.RollbackException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Application complete : migration, jeu de donnees, scenarios fonctionnels,
 * requetes JPQL/Criteria, verrouillage optimiste, cache de second niveau et
 * rapport de performance.
 */
public class MainApp {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public static void main(String[] args) throws Exception {
        LoggingConfig.installer();

        titre("TP CAPSTONE : APPLICATION DE RESERVATION DE SALLES");
        appliquerMigration();

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(DatabaseConfig.UNITE_PERSISTANCE);
        EntityManager em = emf.createEntityManager();
        try {
            DataInitializer initializer = new DataInitializer(em);
            initializer.initData();
            initializer.renseignerDescriptions();

            ReservationService service = new ReservationService(em);
            scenariosFonctionnels(service);
            requetesJpqlEtCriteria(service);
            verrouillageOptimiste(emf);
            cacheDeuxiemeNiveau(emf);
            nouveauRapport(emf);
        } finally {
            em.close();
            emf.close();
        }
    }

    private static void appliquerMigration() throws Exception {
        titre("1. MIGRATION DE LA BASE DE DONNEES");
        MigrationExecutor executor = new MigrationExecutor(
                DatabaseConfig.URL, DatabaseConfig.UTILISATEUR, DatabaseConfig.MOT_DE_PASSE);

        System.out.println("Tables presentes avant migration : " + executor.tableExiste("salles"));
        executor.executeMigration(DatabaseConfig.SCRIPT_SCHEMA);
        System.out.println("Colonnes de SALLES apres v1.0    : " + executor.colonnes("salles"));

        executor.executeMigration(DatabaseConfig.SCRIPT_MIGRATION);
        List<String> apres = executor.colonnes("salles");
        System.out.println("Colonnes de SALLES apres v2.0    : " + apres);
        System.out.println("Colonne description ajoutee      : " + apres.contains("description"));
    }

    private static void scenariosFonctionnels(ReservationService service) {
        titre("2. SCENARIOS FONCTIONNELS");

        LocalDate jour = LocalDate.now().plusDays(3);
        LocalDateTime debut = jour.atTime(LocalTime.of(9, 0));
        LocalDateTime fin = jour.atTime(LocalTime.of(10, 30));

        Reservation creee = service.creerReservation("alice.dupont@example.com", "Salle C", debut, fin);
        System.out.println("Creation            : " + creee);

        try {
            service.creerReservation("bruno.martin@example.com", "Salle C",
                    debut.plusMinutes(30), fin.plusMinutes(30));
            System.out.println("Conflit            : aucun (inattendu)");
        } catch (ConflitReservationException e) {
            System.out.println("Conflit detecte     : " + e.getMessage());
        }

        Reservation deplacee = service.deplacerReservation(creee.getId(),
                jour.atTime(LocalTime.of(14, 0)), jour.atTime(LocalTime.of(15, 30)));
        System.out.println("Deplacement         : " + deplacee.getDateDebut().format(HEURE)
                + " -> " + deplacee.getDateFin().format(HEURE));

        boolean annulee = service.annulerReservation(creee.getId());
        System.out.println("Annulation          : " + (annulee ? "effectuee" : "impossible"));
        System.out.println("Reservations restantes : " + service.compterReservations());
    }

    private static void requetesJpqlEtCriteria(ReservationService service) {
        titre("3. REQUETES JPQL ET CRITERIA API");
        LocalDateTime maintenant = LocalDateTime.now();

        List<Reservation> jpql = service.reservationsParSalle("Salle A", maintenant);
        System.out.println("JPQL  - creneaux de Salle A   : " + jpql.size());
        for (Reservation reservation : jpql) {
            System.out.println("        " + reservation.getDateDebut().format(HEURE)
                    + " -> " + reservation.getDateFin().format(HEURE)
                    + " | " + reservation.getUtilisateur());
        }

        List<Reservation> criteria = service.rechercherReservations("Salle A", maintenant, 15);
        System.out.println("Criteria - capacite >= 15     : " + criteria.size() + " reservation(s)");

        List<Reservation> filtreVide = service.rechercherReservations("Salle A", maintenant, 200);
        System.out.println("Criteria - capacite >= 200    : " + filtreVide.size() + " reservation(s)");
    }

    private static void verrouillageOptimiste(EntityManagerFactory emf) {
        titre("4. VERROUILLAGE OPTIMISTE (@Version)");

        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();
        try {
            Salle vue1 = em1.find(Salle.class, idSalle(em1, "Salle B"));
            Salle vue2 = em2.find(Salle.class, idSalle(em2, "Salle B"));
            System.out.println("Version lue par la session 1  : " + vue1.getVersion());
            System.out.println("Version lue par la session 2  : " + vue2.getVersion());

            EntityTransaction tx1 = em1.getTransaction();
            tx1.begin();
            vue1.setCapacite(30);
            tx1.commit();
            System.out.println("Session 1 : capacite mise a jour, version = " + vue1.getVersion());

            EntityTransaction tx2 = em2.getTransaction();
            tx2.begin();
            boolean conflit = false;
            try {
                vue2.setCapacite(45);
                tx2.commit();
            } catch (OptimisticLockException | RollbackException e) {
                conflit = true;
                if (tx2.isActive()) {
                    tx2.rollback();
                }
                LOGGER.warning("Conflit detecte, veuillez reessayer.");
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.println("Exception remontee           : " + cause.getClass().getSimpleName());
            } finally {
                em2.clear();
            }
            if (conflit) {
                System.out.println("Session 2 : conflit d'ecriture detecte, modification refusee");
            } else {
                System.out.println("Session 2 : mise a jour acceptee (inattendu)");
            }

            Salle relue = em1.find(Salle.class, vue1.getId());
            em1.refresh(relue);
            System.out.println("Valeur finale en base        : capacite = " + relue.getCapacite()
                    + ", version = " + relue.getVersion());
        } finally {
            em1.close();
            em2.close();
        }
    }

    private static void cacheDeuxiemeNiveau(EntityManagerFactory emf) {
        titre("5. CACHE DE SECOND NIVEAU");
        SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        Long salleId = idSalle(emf, "Salle A");

        sessionFactory.getCache().evictAll();
        System.out.println("Salle A presente dans le cache L2 : "
                + sessionFactory.getCache().contains(Salle.class, salleId));

        long[] avant = PerformanceReport.compteurs(stats);
        lireSalle(emf, salleId, "Lecture 1 (session 1)");
        long[] apres = PerformanceReport.compteurs(stats);
        System.out.println("        lectures 1 : misses = " + (apres[1] - avant[1])
                + ", hits = " + (apres[0] - avant[0]));

        avant = apres;
        lireSalle(emf, salleId, "Lecture 2 (session 2)");
        apres = PerformanceReport.compteurs(stats);
        System.out.println("        lectures 2 : misses = " + (apres[1] - avant[1])
                + ", hits = " + (apres[0] - avant[0]));

        avant = apres;
        lireSalle(emf, salleId, "Lecture 3 (session 3)");
        apres = PerformanceReport.compteurs(stats);
        System.out.println("        lectures 3 : misses = " + (apres[1] - avant[1])
                + ", hits = " + (apres[0] - avant[0]));

        System.out.println("Salle A presente dans le cache L2 : "
                + sessionFactory.getCache().contains(Salle.class, salleId));
    }

    private static void lireSalle(EntityManagerFactory emf, Long salleId, String libelle) {
        EntityManager em = emf.createEntityManager();
        try {
            Salle salle = em.find(Salle.class, salleId);
            System.out.println(libelle + "          : " + salle + ", description = " + salle.getDescription());
        } finally {
            em.close();
        }
    }

    private static void nouveauRapport(EntityManagerFactory emf) {
        titre("6. RAPPORT DE PERFORMANCE");
        EntityManager em = emf.createEntityManager();
        try {
            PerformanceReport rapport = new PerformanceReport(em);
            rapport.printReport();
            rapport.afficherRegions();
        } finally {
            em.close();
        }
    }

    private static Long idSalle(EntityManager em, String nom) {
        return em.createQuery("SELECT s.id FROM Salle s WHERE s.nom = :nom", Long.class)
                .setParameter("nom", nom)
                .getSingleResult();
    }

    private static Long idSalle(EntityManagerFactory emf, String nom) {
        EntityManager em = emf.createEntityManager();
        try {
            return idSalle(em, nom);
        } finally {
            em.close();
        }
    }

    private static void titre(String texte) {
        System.out.println();
        System.out.println("=== " + texte + " ===");
    }
}
