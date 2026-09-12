package com.ibizabroker.bibliotheque.entity;

/**
 * DTO d'entrée du module Réservation.
 *
 * Le client ne fournit QUE livreId et adherentId — tout le reste
 * (dates, statut) est déterminé par le serveur.
 *
 * Note : à partir de la Séance 4, adherentId ne sera plus honoré pour
 * un ADHERENT (l'identité viendra du token JWT — règle RS-04).
 */
public class ReservationRequest {

    private Integer livreId;

    private Integer adherentId;

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
}
