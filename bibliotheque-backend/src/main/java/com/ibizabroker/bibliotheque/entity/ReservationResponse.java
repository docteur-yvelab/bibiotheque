package com.ibizabroker.bibliotheque.entity;

public class ReservationResponse {

    private Integer reservationId;
    private Integer livreId;
    private Integer adherentId;
    private java.util.Date dateReservation;
    private java.util.Date dateExpiration;
    private ReservationStatus statut;

    public ReservationResponse(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.livreId = reservation.getLivreId();
        this.adherentId = reservation.getAdherentId();
        this.dateReservation = reservation.getDateReservation();
        this.dateExpiration = reservation.getDateExpiration();
        this.statut = reservation.getStatut();
    }

    public Integer getReservationId() {
        return reservationId;
    }

    public Integer getLivreId() {
        return livreId;
    }

    public Integer getAdherentId() {
        return adherentId;
    }

    public java.util.Date getDateReservation() {
        return dateReservation;
    }

    public java.util.Date getDateExpiration() {
        return dateExpiration;
    }

    public ReservationStatus getStatut() {
        return statut;
    }
}
