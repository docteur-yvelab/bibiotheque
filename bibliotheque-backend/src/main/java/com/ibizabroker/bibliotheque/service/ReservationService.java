package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.ForbiddenException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import com.ibizabroker.bibliotheque.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * POST /api/reservations — Créer une réservation
     *
     * RG-01 : On ne peut réserver qu'un livre indisponible (noOfCopies == 0)
     * RG-02 : Un adhérent ne peut avoir qu'une seule réservation active sur un même livre
     * RG-03 : Un adhérent ne peut pas dépasser 3 réservations actives simultanées
     * RG-04 : dateExpiration = dateReservation + 7 jours
     * RS-04 : L'identité vient du token, jamais du corps de la requête pour un ADHERENT.
     */
    public ReservationResponse creerReservation(ReservationRequest request) {
        if (request.getLivreId() == null) {
            throw new IllegalArgumentException("Le champ 'livreId' est obligatoire.");
        }

        // RS-04 : un ADHERENT réserve forcément pour lui-même, quel que soit le
        // adherentId envoyé dans le corps de la requête (ignoré/écrasé ici).
        // Approche (a) du sujet : écrasement silencieux — plus robuste qu'un 403,
        // le client légitime ne peut jamais se tromper.
        // Seul un BIBLIOTHECAIRE peut réserver au nom d'un adhérent précisé.
        Integer adherentId;
        if (securityUtils.estBibliothecaire()) {
            if (request.getAdherentId() == null) {
                throw new IllegalArgumentException("Le champ 'adherentId' est obligatoire pour un BIBLIOTHECAIRE.");
            }
            adherentId = request.getAdherentId();
        } else {
            adherentId = securityUtils.getUtilisateurConnecte().getUserId();
        }

        Books livre = booksRepository.findById(request.getLivreId())
                .orElseThrow(() -> new NotFoundException("Livre avec l'id " + request.getLivreId() + " non trouvé."));

        Users adherent = usersRepository.findById(adherentId)
                .orElseThrow(() -> new NotFoundException("Adhérent avec l'id " + adherentId + " non trouvé."));

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

        Integer adherentIdFinal = adherentId;
        boolean dejaReserve = reservationsActivesLivre.stream()
                .anyMatch(r -> r.getAdherentId().equals(adherentIdFinal));
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
        reservation.setAdherentId(adherentId);
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
     *
     * RS-05 : Un ADHERENT ne reçoit que ses propres réservations, quel que soit
     * le adherentId passé en query (il est simplement ignoré dans ce cas).
     */
    public List<ReservationResponse> listerReservations(ReservationStatus statut, Integer adherentId) {
        if (!securityUtils.estBibliothecaire()) {
            Integer monId = securityUtils.getUtilisateurConnecte().getUserId();
            List<Reservation> mesReservations = (statut != null)
                    ? reservationRepository.findByAdherentIdAndStatut(monId, statut)
                    : reservationRepository.findByAdherentId(monId);
            return toResponses(mesReservations);
        }

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
        return toResponses(reservations);
    }

    private List<ReservationResponse> toResponses(List<Reservation> reservations) {
        List<ReservationResponse> responses = new ArrayList<>();
        for (Reservation r : reservations) {
            responses.add(new ReservationResponse(r));
        }
        return responses;
    }

    /**
     * GET /api/reservations/{id} — Consulter une réservation
     *
     * RS-03 : Un ADHERENT ne peut consulter que ses propres réservations.
     */
    public ReservationResponse consulterReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

        verifierAppartenance(reservation);
        return new ReservationResponse(reservation);
    }

    /**
     * PATCH /api/reservations/{id}/annuler — Annuler une réservation
     *
     * RS-03 : Un ADHERENT ne peut annuler que ses propres réservations.
     * RG-05 : Une réservation ne peut être annulée que si son statut est EN_ATTENTE ou DISPONIBLE
     * RG-06 : Une réservation ANNULEE, EXPIREE ou HONOREE ne peut plus changer d'état
     */
    public ReservationResponse annulerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));

        verifierAppartenance(reservation);

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
     * Réservé au BIBLIOTHECAIRE côté contrôleur (@PreAuthorize) : pas de
     * vérification d'appartenance nécessaire ici, il a accès à tout.
     */
    public void supprimerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation avec l'id " + id + " non trouvée."));
        reservationRepository.delete(reservation);
    }

    /**
     * RS-03 : un ADHERENT ne peut agir que sur ses propres réservations.
     * Un BIBLIOTHECAIRE passe toujours ce contrôle.
     */
    private void verifierAppartenance(Reservation reservation) {
        if (securityUtils.estBibliothecaire()) {
            return;
        }
        Integer monId = securityUtils.getUtilisateurConnecte().getUserId();
        if (!reservation.getAdherentId().equals(monId)) {
            throw new ForbiddenException("Vous n'avez pas accès à cette réservation.");
        }
    }
}
