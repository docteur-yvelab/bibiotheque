package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.*;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
     * RS-04 : L'identité vient du token JWT, pas du body (pour ADHERENT)
     */
    public ReservationResponse creerReservation(ReservationRequest request, Integer userId, boolean isBiblio) {
        // Validation des champs obligatoires
        if (request.getLivreId() == null) {
            throw new IllegalArgumentException("Le champ 'livreId' est obligatoire.");
        }

        Integer adherentId;
        if (isBiblio) {
            // BIBLIOTHECAIRE : peut créer pour n'importe qui
            if (request.getAdherentId() == null) {
                throw new IllegalArgumentException("Le champ 'adherentId' est obligatoire pour un BIBLIOTHECAIRE.");
            }
            adherentId = request.getAdherentId();
            // Vérifier que l'adhérent cible existe
            usersRepository.findById(adherentId)
                    .orElseThrow(() -> new NotFoundException("Adhérent avec l'id " + adherentId + " non trouvé."));
        } else {
            // RS-04 : ADHERENT — le corps est ignoré, on utilise le token JWT
            adherentId = userId;
        }

        // Vérifier que le livre existe
        Books livre = booksRepository.findById(request.getLivreId())
                .orElseThrow(() -> new NotFoundException("Livre avec l'id " + request.getLivreId() + " non trouvé."));

        // RG-01 : On ne peut réserver qu'un livre indisponible (noOfCopies == 0)
        if (livre.getNoOfCopies() > 0) {
            throw new ConflictException("RG-01 : Le livre '" + livre.getBookName() + "' est disponible, réservation impossible.");
        }

        // RG-02 : Un adhérent ne peut avoir qu'une seule réservation active sur un même livre
        List<Reservation> reservationsEnAttente = reservationRepository
                .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.EN_ATTENTE);
        List<Reservation> reservationsDisponibles = reservationRepository
                .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.DISPONIBLE);
        List<Reservation> reservationsActivesLivre = new ArrayList<>(reservationsEnAttente);
        reservationsActivesLivre.addAll(reservationsDisponibles);

        boolean dejaReserve = reservationsActivesLivre.stream()
                .anyMatch(r -> r.getAdherentId().equals(adherentId));
        if (dejaReserve) {
            throw new ConflictException("RG-02 : L'adhérent a déjà une réservation active pour ce livre.");
        }

        // RG-03 : Un adhérent ne peut pas dépasser 3 réservations actives simultanées
        List<Reservation> reservationsActivesAdherent = reservationRepository
                .findByAdherentIdAndStatutIn(adherentId,
                        Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE));
        if (reservationsActivesAdherent.size() >= 3) {
            throw new ConflictException("RG-03 : L'adhérent ne peut pas dépasser 3 réservations actives simultanées.");
        }

        // Création de la réservation
        Reservation reservation = new Reservation();
        reservation.setLivreId(request.getLivreId());
        reservation.setAdherentId(adherentId); // RS-04 : toujours le token pour un ADHERENT
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
     * GET /api/reservations — Lister les réservations
     *
     * RS-05 : Un ADHERENT ne voit QUE ses propres réservations.
     *         Un BIBLIOTHECAIRE voit toutes les réservations.
     *         Le BIBLIOTHECAIRE peut filtrer par adherentId.
     */
    public List<ReservationResponse> listerReservations(
            ReservationStatus statut, Integer filterAdherentId,
            Integer userId, boolean isBiblio) {

        List<Reservation> reservations;

        if (isBiblio) {
            // BIBLIOTHECAIRE : voit tout, filtrable par statut et/ou adherentId
            if (statut != null && filterAdherentId != null) {
                reservations = reservationRepository.findByAdherentIdAndStatut(filterAdherentId, statut);
            } else if (statut != null) {
                reservations = reservationRepository.findByStatut(statut);
            } else if (filterAdherentId != null) {
                reservations = reservationRepository.findByAdherentId(filterAdherentId);
            } else {
                reservations = reservationRepository.findAll();
            }
        } else {
            // RS-05 : ADHERENT : voit uniquement ses réservations
            if (statut != null) {
                reservations = reservationRepository.findByAdherentIdAndStatut(userId, statut);
            } else {
                reservations = reservationRepository.findByAdherentId(userId);
            }
        }

        List<ReservationResponse> responses = new ArrayList<>();
        for (Reservation r : reservations) {
            responses.add(new ReservationResponse(r));
        }
        return responses;
    }

    /**
     * GET /api/reservations/{id} — Consulter une réservation
     *
     * RS-03 : ADHERENT ne peut consulter que SES réservations.
     *         BIBLIOTHECAIRE peut consulter toutes les réservations.
     */
    public ReservationResponse consulterReservation(Integer id, Integer userId, boolean isBiblio) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

        // RS-03 : Vérifier que la réservation appartient à l'ADHERENT
        if (!isBiblio && !reservation.getAdherentId().equals(userId)) {
            throw new AccessDeniedException("RS-03 : Accès refusé — cette réservation ne vous appartient pas.");
        }

        return new ReservationResponse(reservation);
    }

    /**
     * PATCH /api/reservations/{id}/annuler — Annuler une réservation
     *
     * RG-05 : Une réservation ne peut être annulée que si son statut est EN_ATTENTE ou DISPONIBLE
     * RG-06 : Une réservation ANNULEE, EXPIREE ou HONOREE ne peut plus changer d'état
     * RS-03 : ADHERENT ne peut annuler que SES réservations.
     *         BIBLIOTHECAIRE peut annuler n'importe quelle réservation.
     */
    public ReservationResponse annulerReservation(Integer id, Integer userId, boolean isBiblio) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

        // RS-03 : Vérifier que la réservation appartient à l'ADHERENT
        if (!isBiblio && !reservation.getAdherentId().equals(userId)) {
            throw new AccessDeniedException("RS-03 : Accès refusé — cette réservation ne vous appartient pas.");
        }

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
     * (déjà filtré par Spring Security : BIBLIOTHECAIRE uniquement)
     */
    public void supprimerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));
        reservationRepository.delete(reservation);
    }
}
