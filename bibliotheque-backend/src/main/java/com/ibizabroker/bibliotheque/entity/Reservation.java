package com.ibizabroker.bibliotheque.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "Reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reservationId;

    @Column(nullable = false)
    private Integer livreId;

    @Column(nullable = false)
    private Integer adherentId;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonSerialize(using = JsonDataSerializer.class)
    @Column(nullable = false)
    private Date dateReservation;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonSerialize(using = JsonDataSerializer.class)
    @Column(nullable = false)
    private Date dateExpiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus statut;

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

    // Setters
    public void setReservationId(Integer reservationId) {
        this.reservationId = reservationId;
    }

    public void setLivreId(Integer livreId) {
        this.livreId = livreId;
    }

    public void setAdherentId(Integer adherentId) {
        this.adherentId = adherentId;
    }

    public void setDateReservation(Date dateReservation) {
        this.dateReservation = dateReservation;
    }

    public void setDateExpiration(Date dateExpiration) {
        this.dateExpiration = dateExpiration;
    }

    public void setStatut(ReservationStatus statut) {
        this.statut = statut;
    }
}
