package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.service.ConflitReservationException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.DatabaseConfig;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import com.example.util.LoggingConfig;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import javax.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests fonctionnels executes sur le schema reel, cree par le script v1.0
 * puis fait evoluer par le script de migration v2.0.
 */
class ReservationTest {

    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    static void init() throws Exception {
        LoggingConfig.installer();
        MigrationExecutor executor = new MigrationExecutor(
                DatabaseConfig.URL, DatabaseConfig.UTILISATEUR, DatabaseConfig.MOT_DE_PASSE);
        if (!executor.tableExiste("salles")) {
            executor.executeMigration(DatabaseConfig.SCRIPT_SCHEMA);
            executor.executeMigration(DatabaseConfig.SCRIPT_MIGRATION);
        }
        emf = Persistence.createEntityManagerFactory(DatabaseConfig.UNITE_PERSISTANCE);

        EntityManager setup = emf.createEntityManager();
        try {
            boolean vide = setup.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class)
                    .getSingleResult() == 0L;
            if (vide) {
                new DataInitializer(setup).initData();
                new DataInitializer(setup).renseignerDescriptions();
            }
        } finally {
            setup.close();
        }
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
    }

    @AfterAll
    static void close() {
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    @DisplayName("La migration v2.0 ajoute la colonne description")
    void testMigrationAjouteColonneDescription() throws Exception {
        MigrationExecutor executor = new MigrationExecutor(
                DatabaseConfig.URL, DatabaseConfig.UTILISATEUR, DatabaseConfig.MOT_DE_PASSE);
        assertTrue(executor.colonnes("salles").contains("description"));
    }

    @Test
    @DisplayName("Creation d'une reservation valide")
    void testCreateReservation() {
        ReservationService service = new ReservationService(em);
        LocalDate jour = LocalDate.now().plusDays(10);
        Reservation reservation = service.creerReservation("emma.robert@example.com", "Salle A",
                jour.atTime(LocalTime.of(8, 0)), jour.atTime(LocalTime.of(9, 0)));

        assertNotNull(reservation.getId(), "L'identifiant doit etre genere");
        assertEquals(0, reservation.getVersion());
    }

    @Test
    @DisplayName("Un chevauchement de creneau est refuse")
    void testConflitReservation() {
        ReservationService service = new ReservationService(em);
        LocalDate jour = LocalDate.now().plusDays(11);
        service.creerReservation("alice.dupont@example.com", "Salle B",
                jour.atTime(LocalTime.of(10, 0)), jour.atTime(LocalTime.of(12, 0)));

        assertThrows(ConflitReservationException.class, () -> service.creerReservation(
                "bruno.martin@example.com", "Salle B",
                jour.atTime(LocalTime.of(11, 0)), jour.atTime(LocalTime.of(13, 0))));
    }

    @Test
    @DisplayName("Le verrouillage optimiste rejette une mise a jour obsolete")
    void testOptimisticLocking() {
        Long salleId = em.createQuery("SELECT s.id FROM Salle s WHERE s.nom = :nom", Long.class)
                .setParameter("nom", "Salle C")
                .getSingleResult();

        EntityManager session1 = emf.createEntityManager();
        EntityManager session2 = emf.createEntityManager();
        try {
            Salle vue1 = session1.find(Salle.class, salleId);
            Salle vue2 = session2.find(Salle.class, salleId);

            EntityTransaction tx1 = session1.getTransaction();
            tx1.begin();
            vue1.setCapacite(vue1.getCapacite() + 1);
            tx1.commit();

            EntityTransaction tx2 = session2.getTransaction();
            tx2.begin();
            vue2.setCapacite(vue2.getCapacite() + 1);
            PersistenceException erreur = assertThrows(PersistenceException.class, tx2::commit);
            Throwable cause = erreur instanceof OptimisticLockException ? erreur : erreur.getCause();
            assertTrue(cause instanceof OptimisticLockException,
                    "La version obsolete doit lever une OptimisticLockException, obtenu : " + cause);
            if (tx2.isActive()) {
                tx2.rollback();
            }
        } finally {
            session1.close();
            session2.close();
        }
    }

    @Test
    @DisplayName("La deuxieme lecture d'une salle est servie par le cache L2")
    void testCacheSecondNiveau() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        Long salleId = em.createQuery("SELECT s.id FROM Salle s WHERE s.nom = :nom", Long.class)
                .setParameter("nom", "Amphitheatre")
                .getSingleResult();

        stats.clear();
        emf.unwrap(SessionFactory.class).getCache().evictAll();
        long[] avant = PerformanceReport.compteurs(stats);
        lire(salleId);
        long[] apres = PerformanceReport.compteurs(stats);
        long missesPremiereLecture = apres[1] - avant[1];

        avant = apres;
        lire(salleId);
        apres = PerformanceReport.compteurs(stats);

        assertTrue(missesPremiereLecture > 0, "La premiere lecture doit manquer le cache");
        assertEquals(0, apres[1] - avant[1], "La seconde lecture ne doit pas toucher la base");
        assertTrue(apres[0] - avant[0] > 0, "La seconde lecture doit etre un hit");
    }

    @Test
    @DisplayName("Les requetes JPQL et Criteria renvoient les memes reservations")
    void testRequetesJpqlEtCriteria() {
        ReservationService service = new ReservationService(em);
        LocalDateTime maintenant = LocalDateTime.now();

        List<Reservation> jpql = service.reservationsParSalle("Salle A", maintenant);
        List<Reservation> criteria = service.rechercherReservations("Salle A", maintenant, 10);

        assertFalse(jpql.isEmpty(), "La salle A doit avoir des reservations a venir");
        assertEquals(jpql.size(), criteria.size());
    }

    @Test
    @DisplayName("Annulation d'une reservation")
    void testAnnulation() {
        ReservationService service = new ReservationService(em);
        LocalDate jour = LocalDate.now().plusDays(12);
        Reservation reservation = service.creerReservation("david.petit@example.com", "Salle Reunion",
                jour.atTime(LocalTime.of(9, 0)), jour.atTime(LocalTime.of(10, 0)));

        assertTrue(service.annulerReservation(reservation.getId()));
        assertFalse(service.annulerReservation(reservation.getId()));
    }

    private void lire(Long salleId) {
        EntityManager lecture = emf.createEntityManager();
        try {
            assertNotNull(lecture.find(Salle.class, salleId));
        } finally {
            lecture.close();
        }
    }
}
