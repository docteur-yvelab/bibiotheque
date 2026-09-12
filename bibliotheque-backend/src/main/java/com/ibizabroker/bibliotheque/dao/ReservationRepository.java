package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    List<Reservation> findByStatut(ReservationStatus statut);

    List<Reservation> findByAdherentId(Integer adherentId);

    List<Reservation> findByAdherentIdAndStatut(Integer adherentId, ReservationStatus statut);

    List<Reservation> findByLivreIdAndStatut(Integer livreId, ReservationStatus statut);

    List<Reservation> findByAdherentIdAndStatutIn(Integer adherentId, List<ReservationStatus> statuts);
}
