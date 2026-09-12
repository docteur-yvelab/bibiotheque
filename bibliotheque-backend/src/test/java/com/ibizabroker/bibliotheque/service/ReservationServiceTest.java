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
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de ReservationService — aucune base réelle.
 *
 * Tous les repository sont mockés (Mockito) : ces tests valident la
 * logique métier pure, en particulier la règle RG-03 (quota de
 * réservations actives) exigée par la Séance 2.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BooksRepository booksRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private ReservationService reservationService;

    private Books livreIndisponible;
    private Users adherent;

    @BeforeEach
    void setUp() {
        livreIndisponible = new Books();
        livreIndisponible.setBookId(10);
        livreIndisponible.setBookName("Clean Code");
        livreIndisponible.setNoOfCopies(0);

        adherent = new Users();
        adherent.setUserId(5);
        adherent.setName("Thiakou Stive");
        adherent.setUsername("adherent1");
    }

    private ReservationRequest requete(Integer livreId, Integer adherentId) {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(livreId);
        request.setAdherentId(adherentId);
        return request;
    }

    private Reservation reservationActive(Integer livreId, Integer adherentId) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(livreId * 100 + adherentId);
        reservation.setLivreId(livreId);
        reservation.setAdherentId(adherentId);
        reservation.setDateReservation(new Date());
        reservation.setDateExpiration(new Date());
        reservation.setStatut(ReservationStatus.EN_ATTENTE);
        return reservation;
    }

    // ------------------------------------------------------------------
    // RG-03 : maximum 3 réservations actives simultanées
    // ------------------------------------------------------------------

    @Test
    void creerReservation_avecDeuxReservationsActives_doitReussir() {
        // Deux réservations actives déjà en base : la troisième doit passer.
        List<Reservation> actives = Arrays.asList(
                reservationActive(11, 5),
                reservationActive(12, 5));
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(5,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(actives);
        when(booksRepository.findById(10)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(5)).thenReturn(Optional.of(adherent));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.creerReservation(requete(10, 5));

        assertNotNull(response);
        assertEquals(10, response.getLivreId());
        assertEquals(5, response.getAdherentId());
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        // RG-04 : expiration = création + 7 jours (tolérance 1 seconde)
        long ecartJours = (response.getDateExpiration().getTime() - response.getDateReservation().getTime())
                / (1000L * 60 * 60 * 24);
        assertEquals(7L, ecartJours);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void creerReservation_avecTroisReservationsActives_doitEtreRefusee() {
        // Trois réservations actives déjà en base : la quatrième est refusée (RG-03).
        List<Reservation> actives = Arrays.asList(
                reservationActive(11, 5),
                reservationActive(12, 5),
                reservationActive(13, 5));
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(5,
                Arrays.asList(ReservationStatus.EN_ATTENTE, ReservationStatus.DISPONIBLE)))
                .thenReturn(actives);
        when(booksRepository.findById(10)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(5)).thenReturn(Optional.of(adherent));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(10, 5)));

        assertTrue(exception.getMessage().startsWith("RG-03"),
                "Le message doit nommer la règle enfreinte : " + exception.getMessage());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // RG-01 : on ne réserve qu'un livre indisponible
    // ------------------------------------------------------------------

    @Test
    void creerReservation_surLivreDisponible_doitEtreRefusee() {
        Books livreDisponible = new Books();
        livreDisponible.setBookId(20);
        livreDisponible.setBookName("Effective Java");
        livreDisponible.setNoOfCopies(3);

        when(booksRepository.findById(20)).thenReturn(Optional.of(livreDisponible));
        when(usersRepository.findById(5)).thenReturn(Optional.of(adherent));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(20, 5)));

        assertTrue(exception.getMessage().startsWith("RG-01"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // RG-02 : une seule réservation active par adhérent et par livre
    // ------------------------------------------------------------------

    @Test
    void creerReservation_enDoublonSurMemeLivre_doitEtreRefusee() {
        when(reservationRepository.findByLivreIdAndStatut(10, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.singletonList(reservationActive(10, 5)));
        when(booksRepository.findById(10)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(5)).thenReturn(Optional.of(adherent));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(requete(10, 5)));

        assertTrue(exception.getMessage().startsWith("RG-02"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    // ------------------------------------------------------------------
    // Validation 400 : champ obligatoire manquant, nommé précisément
    // ------------------------------------------------------------------

    @Test
    void creerReservation_sansLivreId_doitEtreRefusee_en400() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(requete(null, 5)));

        assertTrue(exception.getMessage().contains("livreId"),
                "Le message doit nommer le champ manquant : " + exception.getMessage());
    }

    @Test
    void creerReservation_sansAdherentId_doitEtreRefusee_en400() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(requete(10, null)));

        assertTrue(exception.getMessage().contains("adherentId"),
                "Le message doit nommer le champ manquant : " + exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 404 : identifiants inconnus
    // ------------------------------------------------------------------

    @Test
    void creerReservation_avecLivreInconnu_doitEtreRefusee_en404() {
        // Le livre est cherché avant l'adhérent : le stub utilisateur serait inutile ici.
        when(booksRepository.findById(99)).thenReturn(Optional.empty());

        com.ibizabroker.bibliotheque.exceptions.NotFoundException exception =
                assertThrows(com.ibizabroker.bibliotheque.exceptions.NotFoundException.class,
                        () -> reservationService.creerReservation(requete(99, 5)));

        assertTrue(exception.getMessage().contains("99"));
    }
}
