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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suite de tests unitaires de la couche service — toutes les règles
 * métier (RG-01 à RG-06) et les règles de sécurité traitées au niveau
 * service (RS-03, RS-04, RS-05).
 *
 * Tous les repositories et SecurityUtils sont mockés (Mockito) :
 * aucune base réelle ne tourne, aucun contexte Spring n'est chargé.
 *
 * Deux « cas » sont fixés par les mocks :
 *  - BIBLIOTHECAIRE : l'adherentId vient du body (isole RG-01→06) ;
 *  - ADHERENT : l'identité vient de SecurityUtils (teste RS-04/05).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    private static final Integer ADHERENT_ID = 42;
    private static final Integer LIVRE_ID = 1;

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

    private ReservationRequest request;
    private Books livreIndisponible;
    private Books livreDisponible;
    private Users adherent;

    @BeforeEach
    void setUp() {
        request = new ReservationRequest();
        request.setLivreId(LIVRE_ID);
        request.setAdherentId(ADHERENT_ID);

        livreIndisponible = new Books();
        livreIndisponible.setBookId(LIVRE_ID);
        livreIndisponible.setBookName("Clean Code");
        livreIndisponible.setNoOfCopies(0);

        livreDisponible = new Books();
        livreDisponible.setBookId(2);
        livreDisponible.setBookName("Effective Java");
        livreDisponible.setNoOfCopies(3);

        adherent = new Users();
        adherent.setUserId(ADHERENT_ID);
        adherent.setUsername("adherent1");
        adherent.setName("Adhérent Un");

        // Cas par défaut : BIBLIOTHECAIRE + livre indisponible + rien en base
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(ADHERENT_ID)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(anyInt(), any(ReservationStatus.class)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ========================================================
    // RG-01 : On ne peut réserver qu'un livre indisponible
    // ========================================================

    @Test
    void creerReservation_livreDisponible_doitEtreRefusee_RG01() {
        request.setLivreId(2);
        when(booksRepository.findById(2)).thenReturn(Optional.of(livreDisponible));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-01"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void creerReservation_livreIndisponible_doitReussir_RG01() {
        ReservationResponse response = reservationService.creerReservation(request);

        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    // ========================================================
    // RG-02 : Une seule réservation active par livre/adhérent
    // ========================================================

    @Test
    void creerReservation_dejaReserveeSurCeLivre_doitEtreRefusee_RG02() {
        Reservation existante = new Reservation();
        existante.setAdherentId(ADHERENT_ID);
        existante.setLivreId(LIVRE_ID);
        existante.setStatut(ReservationStatus.EN_ATTENTE);
        when(reservationRepository.findByLivreIdAndStatut(LIVRE_ID, ReservationStatus.EN_ATTENTE))
                .thenReturn(List.of(existante));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-02"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void creerReservation_dejaReserveeStatutDisponible_doitEtreRefusee_RG02() {
        // Une réservation DISPONIBLE est aussi « active » au sens RG-02
        Reservation existante = new Reservation();
        existante.setAdherentId(ADHERENT_ID);
        existante.setLivreId(LIVRE_ID);
        existante.setStatut(ReservationStatus.DISPONIBLE);
        when(reservationRepository.findByLivreIdAndStatut(LIVRE_ID, ReservationStatus.DISPONIBLE))
                .thenReturn(List.of(existante));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-02"));
    }

    @Test
    void creerReservation_autreLivreReserve_doitReussir_RG02() {
        // Réservation active sur un AUTRE livre : RG-02 ne s'applique pas
        Reservation existanteAutreLivre = new Reservation();
        existanteAutreLivre.setAdherentId(ADHERENT_ID);
        existanteAutreLivre.setLivreId(99);
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(List.of(existanteAutreLivre));

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    // ========================================================
    // RG-03 : Max 3 réservations actives simultanées
    // ========================================================

    @Test
    void creerReservation_avecDeuxReservationsActives_doitReussir() {
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(Arrays.asList(new Reservation(), new Reservation()));

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    void creerReservation_avecTroisReservationsActives_doitEtreRefusee() {
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(Arrays.asList(new Reservation(), new Reservation(), new Reservation()));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(exception.getMessage().contains("RG-03"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ========================================================
    // RG-04 : dateExpiration = dateReservation + 7 jours (serveur)
    // ========================================================

    @Test
    void creerReservation_dateExpiration_doitEtrePlus7Jours_RG04() {
        Date avant = new Date();

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response.getDateReservation());
        assertNotNull(response.getDateExpiration());
        long diffMillis = response.getDateExpiration().getTime() - response.getDateReservation().getTime();
        long diffJours = Math.round(diffMillis / 86400000.0);
        assertEquals(7, diffJours, "RG-04 : expiration = réservation + 7 jours");
        assertTrue(response.getDateReservation().after(new Date(avant.getTime() - 60000)));
    }

    @Test
    void creerReservation_statutInitial_doitEtreEN_ATTENTE() {
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        reservationService.creerReservation(request);
        verify(reservationRepository).save(captor.capture());
        assertEquals(ReservationStatus.EN_ATTENTE, captor.getValue().getStatut());
        assertEquals(LIVRE_ID, captor.getValue().getLivreId());
        assertEquals(ADHERENT_ID, captor.getValue().getAdherentId());
    }

    // ========================================================
    // RS-04 : l'identité vient du token, pas du body
    // ========================================================

    @Test
    void creerReservation_adherentAvecAdherentIdFalsifie_doitUtiliserLeToken_RS04() {
        // Un ADHERENT (pas bibliothécaire) tente de réserver pour l'adhérent 999
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent); // userId = 42
        request.setAdherentId(999); // falsifié dans le body

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        reservationService.creerReservation(request);

        verify(reservationRepository).save(captor.capture());
        assertEquals(ADHERENT_ID, captor.getValue().getAdherentId(),
                "RS-04 : l'adherentId du body doit être ignoré, celui du token utilisé");
    }

    @Test
    void creerReservation_bibliothecaireSansAdherentId_doitEtreRefusee_400() {
        request.setAdherentId(null); // le bibliothécaire doit préciser pour qui

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("adherentId"));
    }

    @Test
    void creerReservation_sansLivreId_doitEtreRefusee_400() {
        request.setLivreId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("livreId"));
    }

    @Test
    void creerReservation_livreInexistant_doitLancer404() {
        when(booksRepository.findById(99)).thenReturn(Optional.empty());
        request.setLivreId(99);

        assertThrows(NotFoundException.class, () -> reservationService.creerReservation(request));
    }

    @Test
    void creerReservation_adherentInexistant_doitLancer404() {
        when(usersRepository.findById(99)).thenReturn(Optional.empty());
        request.setAdherentId(99);

        assertThrows(NotFoundException.class, () -> reservationService.creerReservation(request));
    }

    // ========================================================
    // RS-05 : GET liste — filtrage pour un ADHERENT
    // ========================================================

    @Test
    void listerReservations_adherent_doitRecevoirSesReservations_RS05() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent);
        when(reservationRepository.findByAdherentId(ADHERENT_ID))
                .thenReturn(List.of(uneReservation(1, ADHERENT_ID, ReservationStatus.EN_ATTENTE)));

        List<ReservationResponse> reponses = reservationService.listerReservations(null, null);

        assertEquals(1, reponses.size());
        assertEquals(ADHERENT_ID, reponses.get(0).getAdherentId());
        // Ne doit PAS utiliser findAll ni les filtres query (adherentId ignoré)
        verify(reservationRepository, never()).findAll();
    }

    @Test
    void listerReservations_adherentAvecFiltreStatut_doitFiltrerSesReservations_RS05() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent);
        when(reservationRepository.findByAdherentIdAndStatut(ADHERENT_ID, ReservationStatus.EN_ATTENTE))
                .thenReturn(List.of(uneReservation(1, ADHERENT_ID, ReservationStatus.EN_ATTENTE)));

        List<ReservationResponse> reponses =
                reservationService.listerReservations(ReservationStatus.EN_ATTENTE, 999); // 999 ignoré

        assertEquals(1, reponses.size());
        verify(reservationRepository, never()).findByAdherentId(999);
    }

    @Test
    void listerReservations_bibliothecaire_doitToutVoir() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findAll())
                .thenReturn(Arrays.asList(
                        uneReservation(1, ADHERENT_ID, ReservationStatus.EN_ATTENTE),
                        uneReservation(2, 43, ReservationStatus.DISPONIBLE)));

        List<ReservationResponse> reponses = reservationService.listerReservations(null, null);

        assertEquals(2, reponses.size());
    }

    @Test
    void listerReservations_doitEnrichirAvecLesNoms() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent);
        when(reservationRepository.findByAdherentId(ADHERENT_ID))
                .thenReturn(List.of(uneReservation(LIVRE_ID, ADHERENT_ID, ReservationStatus.EN_ATTENTE)));

        List<ReservationResponse> reponses = reservationService.listerReservations(null, null);

        assertEquals("Clean Code", reponses.get(0).getBookName());
        assertEquals("Adhérent Un", reponses.get(0).getAdherentName());
    }

    // ========================================================
    // RS-03 : consultation/annulation d'une réservation d'autrui
    // ========================================================

    @Test
    void consulterReservation_adherentEtranger_doitEtreRefusee_RS03() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent); // userId 42
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, 43, ReservationStatus.EN_ATTENTE))); // à autrui

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> reservationService.consulterReservation(1));

        assertTrue(ex.getMessage().contains("pas accès"));
    }

    @Test
    void consulterReservation_proprietaire_doitReussir_RS03() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.EN_ATTENTE)));

        ReservationResponse response = reservationService.consulterReservation(1);

        assertEquals(1, response.getReservationId());
    }

    @Test
    void consulterReservation_bibliothecaire_doitToutConsulter_RS03() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, 43, ReservationStatus.EN_ATTENTE))); // pas à lui

        ReservationResponse response = reservationService.consulterReservation(1);

        assertEquals(1, response.getReservationId());
    }

    @Test
    void consulterReservation_inexistante_doitLancer404() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationService.consulterReservation(99));
    }

    // ========================================================
    // RG-05 / RG-06 : transitions d'état à l'annulation
    // ========================================================

    @Test
    void annulerReservation_enAttente_doitReussir_RG05() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.EN_ATTENTE)));

        ReservationResponse response = reservationService.annulerReservation(1);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
    }

    @Test
    void annulerReservation_disponible_doitReussir_RG05() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.DISPONIBLE)));

        ReservationResponse response = reservationService.annulerReservation(1);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
    }

    @Test
    void annulerReservation_annulee_doitEtreRefusee_RG05_RG06() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.ANNULEE)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(ex.getMessage().contains("RG-05") || ex.getMessage().contains("RG-06"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void annulerReservation_expiree_doitEtreRefusee_RG06() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.EXPIREE)));

        assertThrows(ConflictException.class, () -> reservationService.annulerReservation(1));
    }

    @Test
    void annulerReservation_honoree_doitEtreRefusee_RG06() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, ADHERENT_ID, ReservationStatus.HONOREE)));

        assertThrows(ConflictException.class, () -> reservationService.annulerReservation(1));
    }

    @Test
    void annulerReservation_adherentEtranger_doitEtreRefusee_RS03() {
        when(securityUtils.estBibliothecaire()).thenReturn(false);
        when(securityUtils.getUtilisateurConnecte()).thenReturn(adherent);
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(uneReservation(1, 43, ReservationStatus.EN_ATTENTE)));

        assertThrows(ForbiddenException.class, () -> reservationService.annulerReservation(1));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void annulerReservation_inexistante_doitLancer404() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationService.annulerReservation(99));
    }

    // ========================================================
    // DELETE (service) : 404 si inexistante, suppression sinon
    // ========================================================

    @Test
    void supprimerReservation_inexistante_doitLancer404() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservationService.supprimerReservation(99));
    }

    @Test
    void supprimerReservation_existante_doitSupprimer() {
        when(securityUtils.estBibliothecaire()).thenReturn(true);
        Reservation reservation = uneReservation(1, ADHERENT_ID, ReservationStatus.ANNULEE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        reservationService.supprimerReservation(1);

        verify(reservationRepository, times(1)).delete(reservation);
    }

    // ========================================================
    // Helper
    // ========================================================

    private Reservation uneReservation(Integer id, Integer adherentId, ReservationStatus statut) {
        Reservation r = new Reservation();
        r.setReservationId(id);
        r.setLivreId(LIVRE_ID);
        r.setAdherentId(adherentId);
        r.setStatut(statut);
        r.setDateReservation(new Date());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 7);
        r.setDateExpiration(cal.getTime());
        return r;
    }
}
