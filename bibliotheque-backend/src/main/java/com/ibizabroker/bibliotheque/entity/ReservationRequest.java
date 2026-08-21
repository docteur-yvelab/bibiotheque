package com.ibizabroker.bibliotheque.entity;

public class ReservationRequest {

    private Integer livreId;
    private Integer adherentId;

    // Getters
    public Integer getLivreId() {
        return livreId;
    }

    public Integer getAdherentId() {
        return adherentId;
    }

    // Setters
    public void setLivreId(Integer livreId) {
        this.livreId = livreId;
    }

    public void setAdherentId(Integer adherentId) {
        this.adherentId = adherentId;
    }
}
