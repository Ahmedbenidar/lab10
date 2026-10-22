package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Regles metier de la reservation de salles : CRUD, requetes, transactions. */
public class ReservationService {

    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());

    private final EntityManager em;

    public ReservationService(EntityManager em) {
        this.em = em;
    }

    public Optional<Utilisateur> trouverUtilisateurParEmail(String email) {
        TypedQuery<Utilisateur> query = em.createQuery(
                "SELECT u FROM Utilisateur u WHERE u.email = :email", Utilisateur.class);
        query.setParameter("email", email);
        List<Utilisateur> resultats = query.getResultList();
        return resultats.isEmpty() ? Optional.empty() : Optional.of(resultats.get(0));
    }

    public Optional<Salle> trouverSalleParNom(String nom) {
        TypedQuery<Salle> query = em.createQuery("SELECT s FROM Salle s WHERE s.nom = :nom", Salle.class);
        query.setParameter("nom", nom);
        List<Salle> resultats = query.getResultList();
        return resultats.isEmpty() ? Optional.empty() : Optional.of(resultats.get(0));
    }

    /**
     * Cree une reservation apres avoir verifie qu'aucun creneau ne se chevauche
     * pour la salle demandee. Les erreurs metier sont journalisees puis propagees.
     */
    public Reservation creerReservation(String emailUtilisateur, String nomSalle,
                                        LocalDateTime debut, LocalDateTime fin) {
        if (!debut.isBefore(fin)) {
            throw new IllegalArgumentException("La date de fin doit etre posterieure a la date de debut");
        }
        Utilisateur utilisateur = trouverUtilisateurParEmail(emailUtilisateur)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur inconnu : " + emailUtilisateur));
        Salle salle = trouverSalleParNom(nomSalle)
                .orElseThrow(() -> new IllegalArgumentException("Salle inconnue : " + nomSalle));

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            long conflits = compterConflits(salle.getId(), debut, fin);
            if (conflits > 0) {
                throw new ConflitReservationException("La salle " + nomSalle
                        + " est deja reservee sur ce creneau (" + conflits + " reservation(s) en conflit)");
            }
            Reservation reservation = new Reservation(utilisateur, salle, debut, fin);
            em.persist(reservation);
            tx.commit();
            LOGGER.info(() -> "Reservation creee : " + reservation + " pour " + utilisateur.getEmail());
            return reservation;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Echec de la creation de reservation : " + e.getMessage());
            throw e;
        }
    }

    public long compterConflits(Long salleId, LocalDateTime debut, LocalDateTime fin) {
        TypedQuery<Long> query = em.createQuery("SELECT COUNT(r) FROM Reservation r "
                + "WHERE r.salle.id = :salleId AND r.dateDebut < :fin AND :debut < r.dateFin", Long.class);
        query.setParameter("salleId", salleId);
        query.setParameter("debut", debut);
        query.setParameter("fin", fin);
        return query.getSingleResult();
    }

    /** JPQL : reservations d'une salle a partir d'une date. */
    public List<Reservation> reservationsParSalle(String nomSalle, LocalDateTime apres) {
        TypedQuery<Reservation> query = em.createQuery(
                "SELECT r FROM Reservation r JOIN FETCH r.salle JOIN FETCH r.utilisateur "
                        + "WHERE r.salle.nom = :nomSalle AND r.dateDebut > :date ORDER BY r.dateDebut",
                Reservation.class);
        query.setParameter("nomSalle", nomSalle);
        query.setParameter("date", apres);
        return query.getResultList();
    }

    /** Criteria API : meme intention, mais construite dynamiquement. */
    public List<Reservation> rechercherReservations(String nomSalle, LocalDateTime apres, int capaciteMin) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
        Root<Reservation> racine = cq.from(Reservation.class);
        Join<Reservation, Salle> salle = racine.join("salle");

        Predicate predicat = cb.and(
                cb.equal(salle.get("nom"), nomSalle),
                cb.greaterThan(racine.get("dateDebut"), apres),
                cb.greaterThanOrEqualTo(salle.get("capacite"), capaciteMin));
        cq.select(racine).where(predicat).orderBy(cb.asc(racine.get("dateDebut")));
        return em.createQuery(cq).getResultList();
    }

    /** Deplace une reservation en conservant son verrou optimiste. */
    public Reservation deplacerReservation(Long id, LocalDateTime nouveauDebut, LocalDateTime nouvelleFin) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            Reservation reservation = em.find(Reservation.class, id, LockModeType.OPTIMISTIC);
            if (reservation == null) {
                throw new IllegalArgumentException("Reservation inconnue : " + id);
            }
            long conflits = compterConflits(reservation.getSalle().getId(), nouveauDebut, nouvelleFin);
            if (conflits > 0) {
                throw new ConflitReservationException("Le nouveau creneau est deja occupe par une autre reservation");
            }
            reservation.setDateDebut(nouveauDebut);
            reservation.setDateFin(nouvelleFin);
            tx.commit();
            LOGGER.info(() -> "Reservation deplacee : " + reservation);
            return reservation;
        } catch (OptimisticLockException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.warning("Conflit detecte, veuillez reessayer.");
            throw e;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Echec du deplacement : " + e.getMessage());
            throw e;
        }
    }

    public boolean annulerReservation(Long id) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            Reservation reservation = em.find(Reservation.class, id);
            if (reservation == null) {
                tx.rollback();
                LOGGER.warning("Reservation " + id + " introuvable, rien a annuler");
                return false;
            }
            em.remove(reservation);
            tx.commit();
            LOGGER.info("Reservation " + id + " annulee");
            return true;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Echec de l'annulation : " + e.getMessage());
            throw e;
        }
    }

    public long compterReservations() {
        return em.createQuery("SELECT COUNT(r) FROM Reservation r", Long.class).getSingleResult();
    }
}
