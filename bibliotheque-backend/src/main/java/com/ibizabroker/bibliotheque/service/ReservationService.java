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

/**
 * Toute la logique métier du module Réservation.
 *
 * Le contrôleur ne contient aucune règle : il ne fait que déléguer.
 * L'entité Reservation ne sort jamais de cette classe — uniquement des
 * ReservationResponse (exigence DTO).
 *
 * Règles implémentées :
 *  RG-01 (livre indisponible)  -> creerReservation
 *  RG-02 (unicité adhérent+livre actif) -> creerReservation
 *  RG-03 (max 3 actives)       -> creerReservation
 *  RG-04 (expiration = +7j)    -> creerReservation
 *  RG-05 (annulable si EN_ATTENTE/DISPONIBLE) -> annulerReservation
 *  RG-06 (transitions finales) -> annulerReservation
 */
@Service
public class ReservationService {

    /** RG-03 : nombre maximum de réservations actives simultanées. */
    static final int MAX_RESERVATIONS_ACTIVES = 3;

    /** RG-04 : durée de vie d'une réservation avant expiration. */
    static final int DUREE_RESERVATION_JOURS = 7;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Crée une réservation après application de RG-01, RG-02, RG-03 et RG-04.
     *
     * @return le DTO de sortie enrichi (libellés livre + adhérent)
     * @throws IllegalArgumentException 400 si un champ obligatoire manque
     * @throws NotFoundException        404 si livre ou adhérent inconnu
     * @throws ConflictException        409 si RG-01, RG-02 ou RG-03 violée
     */
    public ReservationResponse creerReservation(ReservationRequest request) {
        if (request.getLivreId() == null) {
            throw new IllegalArgumentException("Le champ 'livreId' est obligatoire.");
        }
        // RS-04 : l'identité d'un ADHERENT vient du token, jamais du body.
        // Un BIBLIOTHECAIRE, lui, crée légitimement pour un tiers : il peut
        // fournir n'importe quel adherentId.
        Integer adherentIdEffective;
        if (securityUtils.aLeRole("BIBLIOTHECAIRE")) {
            if (request.getAdherentId() == null) {
                throw new IllegalArgumentException("Le champ 'adherentId' est obligatoire.");
            }
            adherentIdEffective = request.getAdherentId();
        } else {
            adherentIdEffective = securityUtils.getUserIdCourant();
        }

        Books livre = booksRepository.findById(request.getLivreId())
                .orElseThrow(() -> new NotFoundException("Livre introuvable avec l'identifiant " + request.getLivreId() + "."));
        Users adherent = usersRepository.findById(adherentIdEffective)
                .orElseThrow(() -> new NotFoundException("Adhérent introuvable avec l'identifiant " + adherentIdEffective + "."));

        // RG-01 : on ne peut réserver qu'un livre indisponible.
        // Une copie en rayon signifie que l'adhérent peut emprunter directement :
        // faire la file d'attente n'a pas de sens.
        if (livre.getNoOfCopies() != null && livre.getNoOfCopies() > 0) {
            throw new ConflictException("RG-01 : le livre \"" + livre.getBookName()
                    + "\" est disponible (" + livre.getNoOfCopies()
                    + " copie(s) en rayon). Empruntez-le directement au lieu de le réserver.");
        }

        // RG-02 : une seule réservation active par adhérent et par livre.
        List<Reservation> dejaSurCeLivre = reservationRepository
                .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.EN_ATTENTE);
        if (dejaSurCeLivre.isEmpty()) {
            dejaSurCeLivre = reservationRepository
                    .findByLivreIdAndStatut(request.getLivreId(), ReservationStatus.DISPONIBLE);
        }
        for (Reservation existante : dejaSurCeLivre) {
            if (existante.getAdherentId().equals(adherentIdEffective)) {
                throw new ConflictException("RG-02 : vous avez déjà une réservation active sur le livre \""
                        + livre.getBookName() + "\". Un seul exemplaire peut être réservé à la fois.");
            }
        }

        // RG-03 : maximum 3 réservations actives simultanées.
        long actives = countReservationsActives(adherentIdEffective);
        if (actives >= MAX_RESERVATIONS_ACTIVES) {
            throw new ConflictException("RG-03 : quota de " + MAX_RESERVATIONS_ACTIVES
                    + " réservations actives atteint. Annulez-en une avant d'en créer une nouvelle.");
        }

        // Tout est validé : le serveur détermine dates et statut (RG-04).
        Date maintenant = new Date();
        Reservation reservation = new Reservation();
        reservation.setLivreId(request.getLivreId());
        reservation.setAdherentId(adherentIdEffective);
        reservation.setDateReservation(maintenant);
        reservation.setDateExpiration(ajouterJours(maintenant, DUREE_RESERVATION_JOURS));
        reservation.setStatut(ReservationStatus.EN_ATTENTE);

        Reservation enregistree = reservationRepository.save(reservation);
        return versResponse(enregistree, livre, adherent);
    }

    /**
     * Liste toutes les réservations, avec filtres optionnels.
     * RS-05 : un ADHERENT ne voit jamais que les siennes, même s'il tente
     * de passer l'adherentId d'un autre en query param. Un BIBLIOTHECAIRE
     * utilise librement les filtres.
     */
    public List<ReservationResponse> listerReservations(ReservationStatus statut, Integer adherentId) {
        Integer adherentIdEffective = adherentId;
        if (!securityUtils.aLeRole("BIBLIOTHECAIRE")) {
            adherentIdEffective = securityUtils.getUserIdCourant();
        }
        List<Reservation> reservations;
        if (statut != null && adherentIdEffective != null) {
            reservations = new ArrayList<>();
            reservations.addAll(reservationRepository.findByAdherentIdAndStatut(adherentIdEffective, statut));
        } else if (statut != null) {
            reservations = reservationRepository.findByStatut(statut);
        } else if (adherentIdEffective != null) {
            reservations = reservationRepository.findByAdherentId(adherentIdEffective);
        } else {
            reservations = reservationRepository.findAll();
        }
        return versResponses(reservations);
    }

    /** Consultation d'une réservation. RS-03 : un ADHERENT ne voit que les siennes. */
    public ReservationResponse obtenirReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation introuvable avec l'identifiant " + id + "."));
        verifierAppartenance(reservation);
        return versResponse(reservation, null, null);
    }

    /**
     * Annule une réservation si RG-05 le permet.
     *
     * @throws NotFoundException  404 si inconnue
     * @throws ConflictException  409 si RG-05 (statut non annulable) ou RG-06 (transition finale)
     */
    public ReservationResponse annulerReservation(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réservation introuvable avec l'identifiant " + id + "."));

        // RS-03 : vérification de propriété avant toute mutation.
        verifierAppartenance(reservation);

        // RG-05 / RG-06 : seules EN_ATTENTE et DISPONIBLE sont annulables.
        // Toute autre valeur (ANNULEE, EXPIREE, HONOREE) est un état final
        // qui ne peut plus changer (RG-06).
        if (reservation.getStatut() != ReservationStatus.EN_ATTENTE
                && reservation.getStatut() != ReservationStatus.DISPONIBLE) {
            throw new ConflictException("RG-05 : une réservation au statut '" + reservation.getStatut()
                    + "' ne peut plus être annulée (seules les réservations EN_ATTENTE ou DISPONIBLE le peuvent).");
        }

        reservation.setStatut(ReservationStatus.ANNULEE);
        Reservation enregistree = reservationRepository.save(reservation);
        return versResponse(enregistree, null, null);
    }

    /** Suppression physique (gestion de la bibliothèque). @throws NotFoundException 404 si inconnue. */
    public void supprimerReservation(Integer id) {
        if (!reservationRepository.existsById(id)) {
            throw new NotFoundException("Réservation introuvable avec l'identifiant " + id + ".");
        }
        reservationRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Helpers privés
    // ------------------------------------------------------------------

    /**
     * RS-03 : un ADHERENT ne peut agir que sur ses propres réservations.
     * Un BIBLIOTHECAIRE a accès à tout.
     */
    private void verifierAppartenance(Reservation reservation) {
        if (securityUtils.aLeRole("BIBLIOTHECAIRE")) {
            return;
        }
        if (!reservation.getAdherentId().equals(securityUtils.getUserIdCourant())) {
            throw new ForbiddenException("Cette réservation ne vous appartient pas.");
        }
    }

    /** Compte les réservations actives (EN_ATTENTE ou DISPONIBLE) d'un adhérent. */
    private long countReservationsActives(Integer adherentId) {
        List<Reservation> actives = reservationRepository.findByAdherentIdAndStatutIn(
                adherentId, Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE));
        return actives.size();
    }

    /** RG-04 : date + n jours calendaires. */
    private Date ajouterJours(Date date, int jours) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, jours);
        return calendar.getTime();
    }

    /** Conversion entité -> DTO de sortie, avec enrichissement des libellés. */
    private ReservationResponse versResponse(Reservation reservation, Books livre, Users adherent) {
        ReservationResponse response = ReservationResponse.from(reservation);
        if (livre != null) {
            response.setBookName(livre.getBookName());
        } else {
            booksRepository.findById(reservation.getLivreId())
                    .ifPresent(l -> response.setBookName(l.getBookName()));
        }
        if (adherent != null) {
            response.setAdherentName(adherent.getName());
        } else {
            usersRepository.findById(reservation.getAdherentId())
                    .ifPresent(u -> response.setAdherentName(u.getName()));
        }
        return response;
    }

    private List<ReservationResponse> versResponses(List<Reservation> reservations) {
        List<ReservationResponse> responses = new ArrayList<>();
        for (Reservation reservation : reservations) {
            responses.add(versResponse(reservation, null, null));
        }
        return responses;
    }
}
