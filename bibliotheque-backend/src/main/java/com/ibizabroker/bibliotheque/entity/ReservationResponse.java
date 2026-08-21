package com.ibizabroker.bibliotheque.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Date;

public class ReservationResponse {

    private Integer reservationId;
    private Integer livreId;
    private Integer adherentId;

    @JsonSerialize(using = JsonDataSerializer.class)
    private Date dateReservation;

    @JsonSerialize(using = JsonDataSerializer.class)
    private Date dateExpiration;

    private ReservationStatus statut;

    // Constructeur à partir de l'entité Reservation
    public ReservationResponse(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.livreId = reservation.getLivreId();
        this.adherentId = reservation.getAdherentId();
        this.dateReservation = reservation.getDateReservation();
        this.dateExpiration = reservation.getDateExpiration();
        this.statut = reservation.getStatut();
    }

    // Getters
    public Integer getReservationId() {
        return reservationId;
    }

    public Integer getLivreId() {
        return livreId;
    }

    public Integer getAdherentId() {
        return adherentId;
    }

    public Date getDateReservation() {
        return dateReservation;
    }

    public Date getDateExpiration() {
        return dateExpiration;
    }

    public ReservationStatus getStatut() {
        return statut;
    }
}
