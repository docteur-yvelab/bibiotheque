package com.ibizabroker.bibliotheque.entity;

import java.util.Date;

/**
 * DTO de sortie du module Réservation.
 *
 * L'entité Reservation ne sort jamais du service (exigence Séance 2).
 * Les champs bookName / adherentName sont enrichis par le service pour
 * permettre au frontend Séance 3 d'afficher des libellés lisibles
 * (et non de simples identifiants bruts).
 */
public class ReservationResponse {

    private Integer reservationId;
    private Integer livreId;
    private Integer adherentId;
    private String bookName;
    private String adherentName;
    private Date dateReservation;
    private Date dateExpiration;
    private ReservationStatus statut;

    public static ReservationResponse from(Reservation reservation) {
        ReservationResponse response = new ReservationResponse();
        response.setReservationId(reservation.getReservationId());
        response.setLivreId(reservation.getLivreId());
        response.setAdherentId(reservation.getAdherentId());
        response.setDateReservation(reservation.getDateReservation());
        response.setDateExpiration(reservation.getDateExpiration());
        response.setStatut(reservation.getStatut());
        return response;
    }

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

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getAdherentName() {
        return adherentName;
    }

    public void setAdherentName(String adherentName) {
        this.adherentName = adherentName;
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
