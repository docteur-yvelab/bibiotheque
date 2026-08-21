package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.*;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private Books livreDisponible;
    private Users adherent;

    @BeforeEach
    void setUp() {
        livreIndisponible = new Books();
        livreIndisponible.setBookId(1);
        livreIndisponible.setBookName("Livre Test");
        livreIndisponible.setNoOfCopies(0);

        livreDisponible = new Books();
        livreDisponible.setBookId(2);
        livreDisponible.setBookName("Livre Disponible");
        livreDisponible.setNoOfCopies(3);

        adherent = new Users();
        adherent.setUserId(10);
        adherent.setUsername("adherent1");
    }

    // ========================================================
    // RG-01 : On ne peut réserver qu'un livre indisponible
    // ========================================================

    @Test
    void rg01_creerReservation_livreDisponible_doitEchouer() {
        when(booksRepository.findById(2)).thenReturn(Optional.of(livreDisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(2);
        request.setAdherentId(10);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-01"));
        assertTrue(ex.getMessage().contains("disponible"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rg01_creerReservation_livreIndisponible_doitReussir() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(eq(1), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(10), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        verify(reservationRepository, times(1)).save(any());
    }

    // ========================================================
    // RG-02 : Un seul réservation active par livre/adhérent
    // ========================================================

    @Test
    void rg02_reservationExistante_doitEchouer() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));

        Reservation existante = new Reservation();
        existante.setAdherentId(10);
        existante.setStatut(ReservationStatus.EN_ATTENTE);
        existante.setLivreId(1);

        when(reservationRepository.findByLivreIdAndStatut(1, ReservationStatus.EN_ATTENTE))
                .thenReturn(List.of(existante));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-02"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rg02_differentLivre_doitReussir() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));

        // Réservation existante pour un autre livre
        Reservation existante = new Reservation();
        existante.setAdherentId(10);
        existante.setStatut(ReservationStatus.EN_ATTENTE);
        existante.setLivreId(99);

        when(reservationRepository.findByLivreIdAndStatut(1, ReservationStatus.EN_ATTENTE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByLivreIdAndStatut(1, ReservationStatus.DISPONIBLE))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(10), any()))
                .thenReturn(List.of(existante));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        verify(reservationRepository, times(1)).save(any());
    }

    // ========================================================
    // RG-03 : Max 3 réservations actives simultanées
    // ========================================================

    @Test
    void rg03_troisReservationsActives_doitEchouer() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(eq(1), any())).thenReturn(Collections.emptyList());

        List<Reservation> troisActives = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Reservation r = new Reservation();
            r.setAdherentId(10);
            r.setStatut(ReservationStatus.EN_ATTENTE);
            troisActives.add(r);
        }
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(10), any()))
                .thenReturn(troisActives);

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(ex.getMessage().contains("RG-03"));
        assertTrue(ex.getMessage().contains("3 réservations"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rg03_deuxReservationsActives_doitReussir() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(eq(1), any())).thenReturn(Collections.emptyList());

        List<Reservation> deuxActives = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Reservation r = new Reservation();
            r.setAdherentId(10);
            r.setStatut(ReservationStatus.EN_ATTENTE);
            deuxActives.add(r);
        }
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(10), any()))
                .thenReturn(deuxActives);
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        verify(reservationRepository, times(1)).save(any());
    }

    // ========================================================
    // RG-04 : dateExpiration = dateReservation + 7 jours
    // ========================================================

    @Test
    void rg04_dateExpiration_doitEtreDateReservationPlus7Jours() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(10)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(eq(1), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(10), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(10);

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response.getDateReservation());
        assertNotNull(response.getDateExpiration());

        long diffMillis = response.getDateExpiration().getTime() - response.getDateReservation().getTime();
        long diffJours = diffMillis / (1000 * 60 * 60 * 24);

        assertEquals(7, diffJours, "La date d'expiration doit être exactement 7 jours après la réservation");
    }

    // ========================================================
    // RG-05 : Annulation possible si EN_ATTENTE ou DISPONIBLE
    // ========================================================

    @Test
    void rg05_annulerReservation_enAttente_doitReussir() {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setStatut(ReservationStatus.EN_ATTENTE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.annulerReservation(1);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
        verify(reservationRepository, times(1)).save(reservation);
    }

    @Test
    void rg05_annulerReservation_disponible_doitReussir() {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setStatut(ReservationStatus.DISPONIBLE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.annulerReservation(1);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
    }

    // ========================================================
    // RG-06 : Statut ANNULEE/EXPIREE/HONOREE figé
    // ========================================================

    @Test
    void rg06_annulerReservation_annulee_doitEchouer() {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setStatut(ReservationStatus.ANNULEE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(ex.getMessage().contains("RG-05") || ex.getMessage().contains("RG-06"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rg06_annulerReservation_expiree_doitEchouer() {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setStatut(ReservationStatus.EXPIREE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(ex.getMessage().contains("RG-05") || ex.getMessage().contains("RG-06"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rg06_annulerReservation_honoree_doitEchouer() {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setStatut(ReservationStatus.HONOREE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> reservationService.annulerReservation(1));

        assertTrue(ex.getMessage().contains("RG-05") || ex.getMessage().contains("RG-06"));
        verify(reservationRepository, never()).save(any());
    }

    // ========================================================
    // Tests complémentaires
    // ========================================================

    @Test
    void creerReservation_livreInexistant_doitLancerNotFoundException() {
        when(booksRepository.findById(99)).thenReturn(Optional.empty());

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(99);
        request.setAdherentId(10);

        assertThrows(NotFoundException.class,
                () -> reservationService.creerReservation(request));
    }

    @Test
    void creerReservation_adherentInexistant_doitLancerNotFoundException() {
        when(booksRepository.findById(1)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(99)).thenReturn(Optional.empty());

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);
        request.setAdherentId(99);

        assertThrows(NotFoundException.class,
                () -> reservationService.creerReservation(request));
    }

    @Test
    void creerReservation_livreIdManquant_doitLancerIllegalArgumentException() {
        ReservationRequest request = new ReservationRequest();
        request.setAdherentId(10);

        assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(request));
    }

    @Test
    void creerReservation_adherentIdManquant_doitLancerIllegalArgumentException() {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(1);

        assertThrows(IllegalArgumentException.class,
                () -> reservationService.creerReservation(request));
    }

    @Test
    void annulerReservation_inexistante_doitLancerNotFoundException() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> reservationService.annulerReservation(99));
    }
}
