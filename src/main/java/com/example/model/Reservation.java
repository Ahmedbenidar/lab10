package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "date_debut", nullable = false)
    private LocalDateTime dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDateTime dateFin;

    @Version
    private int version;

    public Reservation() {
    }

    public Reservation(Utilisateur utilisateur, Salle salle, LocalDateTime dateDebut, LocalDateTime dateFin) {
        this.utilisateur = utilisateur;
        this.salle = salle;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    /** Deux plages [debut, fin[ se chevauchent si l'une commence avant la fin de l'autre. */
    public boolean chevauche(LocalDateTime debut, LocalDateTime fin) {
        return dateDebut.isBefore(fin) && debut.isBefore(dateFin);
    }

    public Long getId() {
        return id;
    }

    public Salle getSalle() {
        return salle;
    }

    public void setSalle(Salle salle) {
        this.salle = salle;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public int getVersion() {
        return version;
    }

    public Duration duree() {
        return Duration.between(dateDebut, dateFin);
    }

    @Override
    public String toString() {
        return "Reservation#" + id + " " + salle.getNom() + " " + dateDebut + " -> " + dateFin
                + " (" + duree().toMinutes() + " min)";
    }
}
