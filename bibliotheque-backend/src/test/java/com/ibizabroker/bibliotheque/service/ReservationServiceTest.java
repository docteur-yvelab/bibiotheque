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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de ReservationService — aucune base réelle.
 *
 * Repository ET SecurityUtils sont mockés : ces tests valident la logique
 * métier pure (RG-01 à RG-06) et les règles de sécurité portées par le
 * service (RS-03 propriété, RS-04 identité du token, RS-05 filtrage).
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Integer ADHERENT1_ID = 5;
    private static final Integer ADHERENT2_ID = 6;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BooksRepository booksRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private ReservationService reservationService;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void simulerAdherent() {
        when(securityUtils.aLeRole("BIBLIOTHECAIRE")).thenReturn(false);
        when(securityUtils.getUserIdCourant()).thenReturn(ADHERENT1_ID);
    }

    private void simulerBibliothecaire() {
        when(securityUtils.aLeRole("BIBLIOTHECAIRE")).thenReturn(true);
    }

    private Books livre(int id, String nom, int copies) {
        Books livre = new Books();
        livre.setBookId(id);
        livre.setBookName(nom);
        livre.setNoOfCopies(copies);
        return livre;
    }

    private Users adherent(int id, String nom) {
        Users utilisateur = new Users();
        utilisateur.setUserId(id);
        utilisateur.setName(nom);
        utilisateur.setUsername("user" + id);
        return utilisateur;
    }

    private ReservationRequest requete(Integer livreId, Integer adherentId) {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(livreId);
        request.setAdherentId(adherentId);
        return request;
    }

    private Reservation reservation(Integer id, Integer livreId, Integer adherentId, ReservationStatus statut) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(id);
        reservation.setLivreId(livreId);
        reservation.setAdherentId(adherentId);
        reservation.setDateReservation(new Date());
        reservation.setDateExpiration(new Date());
        reservation.setStatut(statut);
        return reservation;
    }

    private Reservation reservationActive(int livreId, int adherentId) {
        return reservation(livreId * 100 + adherentId, livreId, adherentId, ReservationStatus.EN_ATTENTE);
    }

    // ------------------------------------------------------------------
    // RG-03 : maximum 3 réservations actives simultanées
    // ------------------------------------------------------------------

    @Test
    void creerReservation_avecDeuxReservationsActives_doitReussir() {
        simulerAdherent();
        List<Reservation> actives = Arrays.asList(
                reservationActive(11, ADHERENT1_ID),
                reservationActive(12, ADHERENT1_ID));
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(ADHERENT1_ID,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(actives);
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.creerReservation(requete(10, null));

        assertNotNull(response);
        assertEquals(ADHERENT1_ID, response.getAdherentId());
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        // RG-04 : expiration = création + 7 jours
        long ecartJours = (response.getDateExpiration().getTime() - response.getDateReservation().getTime())
                / (1000L * 60 * 60 * 24);
        assertEquals(7L, ecartJours);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void creerReservation_avecTroisReservationsActives_doitEtreRefusee() {
        simulerAdherent();
        List<Reservation> actives = Arrays.asList(
                reservationActive(11, ADHERENT1_ID),
                reservationActive(12, ADHERENT1_ID),
                reservationActive(13, ADHERENT1_ID));
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(ADHERENT1_ID,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(actives);
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(10, null)));

        assertTrue(exception.getMessage().startsWith("RG-03"),
                "Le message doit nommer la règle enfreinte : " + exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // RG-01 : on ne réserve qu'un livre indisponible
    // ------------------------------------------------------------------

    @Test
    void creerReservation_surLivreDisponible_doitEtreRefusee() {
        simulerAdherent();
        when(booksRepository.findById(20)).thenReturn(Optional.of(livre(20, "Effective Java", 3)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(20, null)));

        assertTrue(exception.getMessage().startsWith("RG-01"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // RG-02 : une seule réservation active par adhérent et par livre
    // ------------------------------------------------------------------

    @Test
    void creerReservation_enDoublonSurMemeLivre_doitEtreRefusee() {
        simulerAdherent();
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.singletonList(reservationActive(10, ADHERENT1_ID)));
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(10, null)));

        assertTrue(exception.getMessage().startsWith("RG-02"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // RS-04 : l'identité d'un ADHERENT vient du token, jamais du body
    // ------------------------------------------------------------------

    @Test
    void creerReservation_adherentAvecAdherentIdFalsifie_doitIgnorerLeBody() {
        simulerAdherent();
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(ADHERENT1_ID,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(Collections.emptyList());
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Le body prétend être l'adhérent 2 : il doit être ignoré (RS-04).
        ReservationResponse response = reservationService.creerReservation(requete(10, ADHERENT2_ID));

        assertEquals(ADHERENT1_ID, response.getAdherentId(),
                "L'identité doit venir du token, pas du champ adherentId du body.");
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void creerReservation_bibliothecairePourUnTiers_doitUtiliserLAdherentIdFourni() {
        simulerBibliothecaire();
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(ADHERENT2_ID,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(Collections.emptyList());
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT2_ID)).thenReturn(Optional.of(adherent(ADHERENT2_ID, "Adhérent Deux")));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.creerReservation(requete(10, ADHERENT2_ID));

        assertEquals(ADHERENT2_ID, response.getAdherentId(),
                "Le BIBLIOTHECAIRE crée légitimement pour un tiers (RS-04).");
    }

    @Test
    void creerReservation_bibliothecaireSansAdherentId_doitEtreRefusee_en400() {
        simulerBibliothecaire();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(requete(10, null)));

        assertTrue(exception.getMessage().contains("adherentId"));
    }

    // ------------------------------------------------------------------
    // Validation 400 : champ obligatoire manquant, nommé précisément
    // ------------------------------------------------------------------

    @Test
    void creerReservation_sansLivreId_doitEtreRefusee_en400() {
        // La validation du livreId intervient avant toute résolution d'identité :
        // aucun mock SecurityUtils nécessaire ici.

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(requete(null, null)));

        assertTrue(exception.getMessage().contains("livreId"),
                "Le message doit nommer le champ manquant : " + exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 404 : identifiants inconnus
    // ------------------------------------------------------------------

    @Test
    void creerReservation_avecLivreInconnu_doitEtreRefusee_en404() {
        simulerAdherent();
        // Le livre est cherché avant l'adhérent : pas d'autre stub nécessaire.
        when(booksRepository.findById(99)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> reservationService.creerReservation(requete(99, null)));

        assertTrue(exception.getMessage().contains("99"));
    }

    @Test
    void supprimerReservation_inconnue_doitEtreRefusee_en404() {
        when(reservationRepository.existsById(77)).thenReturn(false);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> reservationService.supprimerReservation(77));

        assertTrue(exception.getMessage().contains("77"));
    }

    // ------------------------------------------------------------------
    // RS-03 : propriété des réservations (lecture, annulation)
    // ------------------------------------------------------------------

    @Test
    void obtenirReservation_parSonProprietaire_doitReussir() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT1_ID, ReservationStatus.EN_ATTENTE)));
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));

        ReservationResponse response = reservationService.obtenirReservation(1);

        assertEquals(1, response.getReservationId());
    }

    @Test
    void obtenirReservation_deAutreAdherent_doitEtreRefusee_en403() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT2_ID, ReservationStatus.EN_ATTENTE)));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> reservationService.obtenirReservation(1));

        assertNotNull(exception.getMessage());
    }

    @Test
    void obtenirReservation_parBibliothecaire_doitReussir() {
        simulerBibliothecaire();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT2_ID, ReservationStatus.EN_ATTENTE)));
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT2_ID)).thenReturn(Optional.of(adherent(ADHERENT2_ID, "Adhérent Deux")));

        ReservationResponse response = reservationService.obtenirReservation(1);

        assertEquals(1, response.getReservationId());
    }

    @Test
    void annulerReservation_deAutreAdherent_doitEtreRefusee_en403() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT2_ID, ReservationStatus.EN_ATTENTE)));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> reservationService.annulerReservation(1));

        verify(reservationRepository, never()).save(any(Reservation.class));
        assertNotNull(exception.getMessage());
    }

    // ------------------------------------------------------------------
    // RS-05 : un ADHERENT ne voit que ses réservations
    // ------------------------------------------------------------------

    @Test
    void listerReservations_adherentAvecFiltreFalsifie_doitFiltrerSurSonIdentite() {
        simulerAdherent();
        when(reservationRepository.findByAdherentId(ADHERENT1_ID)).thenReturn(Collections.emptyList());

        // L'adhérent 1 tente de voir les réservations de l'adhérent 2 : ignoré (RS-05).
        List<ReservationResponse> reponses = reservationService.listerReservations(null, ADHERENT2_ID);

        assertTrue(reponses.isEmpty());
        verify(reservationRepository).findByAdherentId(ADHERENT1_ID);
        verify(reservationRepository, never()).findByAdherentId(ADHERENT2_ID);
        verify(reservationRepository, never()).findAll();
    }

    @Test
    void listerReservations_bibliothecaire_doitVoirTout() {
        simulerBibliothecaire();
        when(reservationRepository.findAll()).thenReturn(Arrays.asList(
                reservation(1, 10, ADHERENT1_ID, ReservationStatus.EN_ATTENTE),
                reservation(2, 11, ADHERENT2_ID, ReservationStatus.HONOREE)));

        List<ReservationResponse> reponses = reservationService.listerReservations(null, null);

        assertEquals(2, reponses.size());
        verify(reservationRepository).findAll();
    }

    // ------------------------------------------------------------------
    // RG-05 / RG-06 : annulation et états finaux
    // ------------------------------------------------------------------

    @Test
    void annulerReservation_enAttente_doitPasserAnnulee() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT1_ID, ReservationStatus.EN_ATTENTE)));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(booksRepository.findById(10)).thenReturn(Optional.of(livre(10, "Clean Code", 0)));
        when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent(ADHERENT1_ID, "Adhérent Un")));

        ReservationResponse response = reservationService.annulerReservation(1);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
    }

    @Test
    void annulerReservation_dejaAnnulee_doitEtreRefusee() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT1_ID, ReservationStatus.ANNULEE)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(exception.getMessage().startsWith("RG-05"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void annulerReservation_honoree_doitEtreRefusee() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT1_ID, ReservationStatus.HONOREE)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(exception.getMessage().startsWith("RG-05"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void annulerReservation_expiree_doitEtreRefusee() {
        simulerAdherent();
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(1, 10, ADHERENT1_ID, ReservationStatus.EXPIREE)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(exception.getMessage().startsWith("RG-05"));
    }

    @Test
    void annulerReservation_inconnue_doitEtreRefusee_en404() {
        // Le 404 intervient avant la vérification de propriété.
        when(reservationRepository.findById(42)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> reservationService.annulerReservation(42));

        assertTrue(exception.getMessage().contains("42"));
    }
}
