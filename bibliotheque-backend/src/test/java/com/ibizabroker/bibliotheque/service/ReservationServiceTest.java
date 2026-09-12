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
import com.ibizabroker.bibliotheque.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Test unitaire de la couche service : tous les repositories et
 * SecurityUtils sont mockés (Mockito) — aucune base réelle ne tourne.
 *
 * Cas fixé : BIBLIOTHECAIRE (l'adherentId vient alors du body), ce qui
 * isole strictement la règle RG-03 du reste de la logique.
 */
@ExtendWith(MockitoExtension.class)
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

    @BeforeEach
    void setUp() {
        request = new ReservationRequest();
        request.setLivreId(LIVRE_ID);
        request.setAdherentId(ADHERENT_ID);

        Books livreIndisponible = new Books();
        livreIndisponible.setBookId(LIVRE_ID);
        livreIndisponible.setBookName("Clean Code");
        livreIndisponible.setNoOfCopies(0);

        Users adherent = new Users();
        adherent.setUserId(ADHERENT_ID);

        when(securityUtils.estBibliothecaire()).thenReturn(true);
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.of(livreIndisponible));
        when(usersRepository.findById(ADHERENT_ID)).thenReturn(Optional.of(adherent));
        when(reservationRepository.findByLivreIdAndStatut(anyInt(), any(ReservationStatus.class)))
                .thenReturn(Collections.emptyList());
    }

    /**
     * Cas 1 : un adhérent avec 2 réservations actives peut en créer une troisième.
     */
    @Test
    void creerReservation_avecDeuxReservationsActives_doitReussir() {
        List<Reservation> deuxReservationsActives = Arrays.asList(new Reservation(), new Reservation());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(deuxReservationsActives);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.creerReservation(request);

        assertNotNull(response);
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    /**
     * Cas 2 : un adhérent avec 3 réservations actives reçoit un refus (RG-03).
     */
    @Test
    void creerReservation_avecTroisReservationsActives_doitEtreRefusee() {
        List<Reservation> troisReservationsActives = Arrays.asList(
                new Reservation(), new Reservation(), new Reservation());
        when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT_ID), anyList()))
                .thenReturn(troisReservationsActives);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.creerReservation(request));

        assertTrue(exception.getMessage().contains("RG-03"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }
}
