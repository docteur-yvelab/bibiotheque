package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.*;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private UsersRepository usersRepository;

    /**
     * POST /api/reservations — Créer une réservation
     *
     * RG-01 : On ne peut réserver qu'un livre indisponible (noOfCopies == 0)
     * RG-02 : Un adhérent ne peut avoir qu'une seule réservation active sur un même livre
     * RG-03 : Un adhérent ne peut pas dépasser 3 réservations actives simultanées
     * RG-04 : dateExpiration = dateReservation + 7 jours
     */
    public ReservationResponse creerReservation(ReservationRequest request) {
        // Validation des champs obligatoires
        if (request.getLivreId() == null) {
            throw new IllegalArgumentException("Le champ 'livreId' est obligatoire.");
        }
        if (request.getAdherentId() == null) {
            throw new IllegalArgumentException("Le champ 'adherentId' est obligatoire.");
        }

        // Vérifier que le livre existe
        Books livre = booksRepository.findById(request.getLivreId())
                .orElseThrow(() -> new NotFoundException("Livre avec l'id " + request.getLivreId() + " non trouvé."));

        // Vérifier que l'adhérent existe
        Users adherent = usersRepository.findById(request.getAdherentId())
                .orElseThrow(() -> new NotFoundException("Adhérent avec l'id " + request.getAdherentId() + " non trouvé."));

        // RG-01 : On ne peut réserver qu'un livre indisponible (noOfCopies == 0)
        if (livre.getNoOfCopies() > 0) {
            throw new ConflictException("RG-01 : Le livre '" + livre.getBookName() + "' est disponible, réservation impossible.");
        }

        // RG-02 : Un adhérent ne peut avoir qu'une seule réservation active sur un même livre
        List<Reservation> reservationsActivesLivre = reservationRepository
                .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.EN_ATTENTE);
        reservationsActivesLivre.addAll(
                reservationRepository.findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.DISPONIBLE));

        boolean dejaReserve = reservationsActivesLivre.stream()
                .anyMatch(r -> r.getAdherentId().equals(request.getAdherentId()));
        if (dejaReserve) {
            throw new ConflictException("RG-02 : L'adhérent a déjà une réservation active pour ce livre.");
        }

        // RG-03 : Un adhérent ne peut pas dépasser 3 réservations actives simultanées
        List<Reservation> reservationsActivesAdherent = reservationRepository
                .findByAdherentIdAndStatutIn(request.getAdherentId(),
                        Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE));
        if (reservationsActivesAdherent.size() >= 3) {
            throw new ConflictException("RG-03 : L'adhérent ne peut pas dépasser 3 réservations actives simultanées.");
        }

        // Création de la réservation
        Reservation reservation = new Reservation();
        reservation.setLivreId(request.getLivreId());
        reservation.setAdherentId(request.getAdherentId());
        reservation.setStatut(ReservationStatus.EN_ATTENTE);

        // RG-04 : dateExpiration = dateReservation + 7 jours (côté serveur uniquement)
        Date now = new Date();
        reservation.setDateReservation(now);

        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DATE, 7);
        reservation.setDateExpiration(cal.getTime());

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationResponse(saved);
    }

    /**
     * GET /api/reservations — Lister les réservations (filtrable par statut et/ou adhérent)
     */
    public List<ReservationResponse> listerReservations(ReservationStatus statut, Integer adherentId) {
        List<Reservation> reservations;

        if (statut != null && adherentId != null) {
            reservations = reservationRepository.findByAdherentIdAndStatut(adherentId, statut);
        } else if (statut != null) {
            reservations = reservationRepository.findByStatut(statut);
        } else if (adherentId != null) {
            reservations = reservationRepository.findByAdherentId(adherentId);
        } else {
            reservations = reservationRepository.findAll();
        }

        List<ReservationResponse> responses = new ArrayList<>();
        for (Reservation r : reservations) {
            responses.add(new ReservationResponse(r));
        }
        return responses;
    }

    /**
     * GET /api/reservations/{id} — Consulter une réservation
     */
    public ReservationResponse consulterReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));
        return new ReservationResponse(reservation);
    }

    /**
     * PATCH /api/reservations/{id}/annuler — Annuler une réservation
     *
     * RG-05 : Une réservation ne peut être annulée que si son statut est EN_ATTENTE ou DISPONIBLE
     * RG-06 : Une réservation ANNULEE, EXPIREE ou HONOREE ne peut plus changer d'état
     */
    public ReservationResponse annulerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

        // RG-05 + RG-06 : Vérification du statut
        if (reservation.getStatut() != ReservationStatus.EN_ATTENTE
                && reservation.getStatut() != ReservationStatus.DISPONIBLE) {
            throw new ConflictException("RG-05/RG-06 : Une réservation avec le statut '"
                    + reservation.getStatut() + "' ne peut pas être annulée.");
        }

        reservation.setStatut(ReservationStatus.ANNULEE);
        Reservation updated = reservationRepository.save(reservation);
        return new ReservationResponse(updated);
    }

    /**
     * DELETE /api/reservations/{id} — Supprimer une réservation
     */
    public void supprimerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));
        reservationRepository.delete(reservation);
    }
}
