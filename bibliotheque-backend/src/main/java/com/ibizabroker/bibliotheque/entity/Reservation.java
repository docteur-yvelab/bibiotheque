package com.ibizabroker.bibliotheque.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer reservationId;

    /** Référence vers Books.bookId (obligatoire). */
    private Integer livreId;

    /** Référence vers Users.userId (obligatoire). */
    private Integer adherentId;

    /** Générée par le serveur, jamais fournie par le client (RG-04). */
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateReservation;

    /** Calculée par le serveur : dateReservation + 7 jours (RG-04). */
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateExpiration;

    @Enumerated(EnumType.STRING)
    private ReservationStatus statut;

    public Integer getReservationId() {
        return reservationId;
    }

    public void setReservationId(Integer reservationId) {
        this.reservationId = reservationId;
    }

    public Integer getLivreId() {
        return livreId;
    }

    public void setLivreId(Integer livreId) {
        this.livreId = livreId;
    }

    public Integer getAdherentId() {
        return adherentId;
    }

    public void setAdherentId(Integer adherentId) {
        this.adherentId = adherentId;
    }

    public Date getDateReservation() {
        return dateReservation;
    }

    public void setDateReservation(Date dateReservation) {
        this.dateReservation = dateReservation;
    }

    public Date getDateExpiration() {
        return dateExpiration;
    }

    public void setDateExpiration(Date dateExpiration) {
        this.dateExpiration = dateExpiration;
    }

    public ReservationStatus getStatut() {
        return statut;
    }

    public void setStatut(ReservationStatus statut) {
        this.statut = statut;
    }
}
