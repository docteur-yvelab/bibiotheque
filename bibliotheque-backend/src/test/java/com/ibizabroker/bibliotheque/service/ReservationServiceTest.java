package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.*;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private Users adherent1;
    private Users adherent2;
    private Users bibliothecaire;

    // IDs simulés
    private static final Integer ADHERENT1_ID = 10;
    private static final Integer ADHERENT2_ID = 20;
    private static final Integer BIBLIO_ID = 1;
    private static final Integer LIVRE_INDISPONIBLE_ID = 1;
    private static final Integer LIVRE_DISPONIBLE_ID = 2;

    @BeforeEach
    void setUp() {
        livreIndisponible = new Books();
        livreIndisponible.setBookId(LIVRE_INDISPONIBLE_ID);
        livreIndisponible.setBookName("Livre Indisponible");
        livreIndisponible.setNoOfCopies(0);

        livreDisponible = new Books();
        livreDisponible.setBookId(LIVRE_DISPONIBLE_ID);
        livreDisponible.setBookName("Livre Disponible");
        livreDisponible.setNoOfCopies(3);

        adherent1 = new Users();
        adherent1.setUserId(ADHERENT1_ID);
        adherent1.setUsername("adherent1");

        adherent2 = new Users();
        adherent2.setUserId(ADHERENT2_ID);
        adherent2.setUsername("adherent2");

        bibliothecaire = new Users();
        bibliothecaire.setUserId(BIBLIO_ID);
        bibliothecaire.setUsername("bibliothecaire");
    }

    // ================================================================
    // RG-01 : On ne peut réserver qu'un livre indisponible
    // ================================================================
    @Nested
    class RG01_LivreIndisponible {

        @Test
        void livreDisponible_doitEchouer() {
            when(booksRepository.findById(LIVRE_DISPONIBLE_ID)).thenReturn(Optional.of(livreDisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_DISPONIBLE_ID);
            request.setAdherentId(ADHERENT1_ID);

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> reservationService.creerReservation(request, ADHERENT1_ID, false));

            assertTrue(ex.getMessage().contains("RG-01"));
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void livreIndisponible_doitReussir() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), any())).thenReturn(Collections.emptyList());
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any())).thenReturn(Collections.emptyList());
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ReservationResponse response = reservationService.creerReservation(request, ADHERENT1_ID, false);

            assertNotNull(response);
            assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
            // RS-04 : le adherentId doit venir du token, pas du body
            assertEquals(ADHERENT1_ID, response.getAdherentId());
            verify(reservationRepository, times(1)).save(any());
        }
    }

    // ================================================================
    // RG-02 : Un seul réservation active par livre/adhérent
    // ================================================================
    @Nested
    class RG02_UneSeuleReservationParLivre {

        @Test
        void reservationExistante_doitEchouer() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));

            Reservation existante = new Reservation();
            existante.setAdherentId(ADHERENT1_ID);
            existante.setStatut(ReservationStatus.EN_ATTENTE);
            existante.setLivreId(LIVRE_INDISPONIBLE_ID);

            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(List.of(existante));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> reservationService.creerReservation(request, ADHERENT1_ID, false));

            assertTrue(ex.getMessage().contains("RG-02"));
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void differentLivre_doitReussir() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));

            Reservation existante = new Reservation();
            existante.setAdherentId(ADHERENT1_ID);
            existante.setStatut(ReservationStatus.EN_ATTENTE);
            existante.setLivreId(99); // Autre livre

            when(reservationRepository.findByLivreIdAndStatut(LIVRE_INDISPONIBLE_ID, ReservationStatus.EN_ATTENTE))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(LIVRE_INDISPONIBLE_ID, ReservationStatus.DISPONIBLE))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any()))
                    .thenReturn(List.of(existante));
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ReservationResponse response = reservationService.creerReservation(request, ADHERENT1_ID, false);

            assertNotNull(response);
            verify(reservationRepository, times(1)).save(any());
        }
    }

    // ================================================================
    // RG-03 : Max 3 réservations actives simultanées
    // ================================================================
    @Nested
    class RG03_Max3Reservations {

        @Test
        void troisReservationsActives_doitEchouer() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());

            List<Reservation> troisActives = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Reservation r = new Reservation();
                r.setAdherentId(ADHERENT1_ID);
                r.setStatut(ReservationStatus.EN_ATTENTE);
                troisActives.add(r);
            }
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any()))
                    .thenReturn(troisActives);

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> reservationService.creerReservation(request, ADHERENT1_ID, false));

            assertTrue(ex.getMessage().contains("RG-03"));
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void deuxReservationsActives_doitReussir() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());

            List<Reservation> deuxActives = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Reservation r = new Reservation();
                r.setAdherentId(ADHERENT1_ID);
                r.setStatut(ReservationStatus.EN_ATTENTE);
                deuxActives.add(r);
            }
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any()))
                    .thenReturn(deuxActives);
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ReservationResponse response = reservationService.creerReservation(request, ADHERENT1_ID, false);

            assertNotNull(response);
            verify(reservationRepository, times(1)).save(any());
        }
    }

    // ================================================================
    // RG-04 : dateExpiration = dateReservation + 7 jours
    // ================================================================
    @Nested
    class RG04_DateExpiration {

        @Test
        void dateExpiration_doitEtreDateReservationPlus7Jours() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any())).thenReturn(Collections.emptyList());
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            ReservationResponse response = reservationService.creerReservation(request, ADHERENT1_ID, false);

            assertNotNull(response.getDateReservation());
            assertNotNull(response.getDateExpiration());

            long diffMillis = response.getDateExpiration().getTime() - response.getDateReservation().getTime();
            long diffJours = diffMillis / (1000 * 60 * 60 * 24);

            assertEquals(7, diffJours, "La date d'expiration doit être exactement 7 jours après la réservation");
        }
    }

    // ================================================================
    // RS-04 : L'identité de l'adhérent vient du TOKEN JWT, pas du body
    // ================================================================
    @Nested
    class RS04_IdentiteDuToken {

        @Test
        void adherent_bodyContientUnAutreAdherentId_doitIgnorerLeBody() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT1_ID)).thenReturn(Optional.of(adherent1));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT1_ID), any())).thenReturn(Collections.emptyList());
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);
            request.setAdherentId(ADHERENT2_ID); // tentative de falsification

            ReservationResponse response = reservationService.creerReservation(request, ADHERENT1_ID, false);

            // Le service doit UTILISER ADHERENT1_ID (du token), PAS ADHERENT2_ID (du body)
            assertEquals(ADHERENT1_ID, response.getAdherentId(),
                    "RS-04 : L'identité doit venir du token JWT, pas du body");
        }

        @Test
        void bibliothecaire_peutCreerPourAutrui() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(ADHERENT2_ID)).thenReturn(Optional.of(adherent2));
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.EN_ATTENTE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByLivreIdAndStatut(eq(LIVRE_INDISPONIBLE_ID), eq(ReservationStatus.DISPONIBLE)))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.findByAdherentIdAndStatutIn(eq(ADHERENT2_ID), any())).thenReturn(Collections.emptyList());
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);
            request.setAdherentId(ADHERENT2_ID);

            ReservationResponse response = reservationService.creerReservation(request, BIBLIO_ID, true);

            assertEquals(ADHERENT2_ID, response.getAdherentId(),
                    "Le BIBLIOTHECAIRE peut créer une réservation pour n'importe quel adhérent");
        }
    }

    // ================================================================
    // RS-03 : ADHERENT ne peut consulter/annuler que SES réservations
    // ================================================================
    @Nested
    class RS03_AccesProprietaireSeul {

        @Test
        void consulter_reservationDUnAutreAdherent_doitEchouer() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setAdherentId(ADHERENT2_ID); // appartient à adherent2
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            assertThrows(AccessDeniedException.class,
                    () -> reservationService.consulterReservation(1, ADHERENT1_ID, false),
                    "RS-03 : Un ADHERENT ne peut pas consulter la réservation d'un autre");
        }

        @Test
        void consulter_saPropreReservation_doitReussir() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setAdherentId(ADHERENT1_ID); // appartient à adherent1
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            ReservationResponse response = reservationService.consulterReservation(1, ADHERENT1_ID, false);
            assertNotNull(response);
            assertEquals(ADHERENT1_ID, response.getAdherentId());
        }

        @Test
        void annuler_reservationDUnAutreAdherent_doitEchouer() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setAdherentId(ADHERENT2_ID);
            reservation.setStatut(ReservationStatus.EN_ATTENTE);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            assertThrows(AccessDeniedException.class,
                    () -> reservationService.annulerReservation(1, ADHERENT1_ID, false),
                    "RS-03 : Un ADHERENT ne peut pas annuler la réservation d'un autre");
        }

        @Test
        void bibliothecaire_peutConsulterToutesLesReservations() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setAdherentId(ADHERENT2_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            ReservationResponse response = reservationService.consulterReservation(1, BIBLIO_ID, true);
            assertNotNull(response);
        }

        @Test
        void bibliothecaire_peutAnnulerToutesLesReservations() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setAdherentId(ADHERENT2_ID);
            reservation.setStatut(ReservationStatus.EN_ATTENTE);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.annulerReservation(1, BIBLIO_ID, true);
            assertEquals(ReservationStatus.ANNULEE, response.getStatut());
        }
    }

    // ================================================================
    // RS-05 : ADHERENT ne voit QUE ses réservations (GET /api/reservations)
    // ================================================================
    @Nested
    class RS05_FiltrageAuto {

        @Test
        void adherent_neVoitQueSesReservations() {
            Reservation r1 = new Reservation();
            r1.setReservationId(1);
            r1.setAdherentId(ADHERENT1_ID);
            r1.setStatut(ReservationStatus.EN_ATTENTE);

            Reservation r2 = new Reservation();
            r2.setReservationId(2);
            r2.setAdherentId(ADHERENT2_ID);
            r2.setStatut(ReservationStatus.EN_ATTENTE);

            // Le repository ne retourne QUE les réservations d'ADHERENT1
            when(reservationRepository.findByAdherentId(ADHERENT1_ID)).thenReturn(List.of(r1));

            List<ReservationResponse> result = reservationService.listerReservations(null, ADHERENT1_ID, false);

            assertEquals(1, result.size());
            assertEquals(ADHERENT1_ID, result.get(0).getAdherentId());
            // Vérifie que findByAdherentId a bien été appelé avec ADHERENT1_ID
            verify(reservationRepository).findByAdherentId(ADHERENT1_ID);
            // Ne doit PAS appeler findAll()
            verify(reservationRepository, never()).findAll();
        }

        @Test
        void bibliothecaire_voitToutesLesReservations() {
            Reservation r1 = new Reservation();
            r1.setReservationId(1);
            r1.setAdherentId(ADHERENT1_ID);

            Reservation r2 = new Reservation();
            r2.setReservationId(2);
            r2.setAdherentId(ADHERENT2_ID);

            when(reservationRepository.findAll()).thenReturn(List.of(r1, r2));

            List<ReservationResponse> result = reservationService.listerReservations(null, BIBLIO_ID, true);

            assertEquals(2, result.size());
            verify(reservationRepository).findAll();
            // Ne doit PAS appeler findByAdherentId()
            verify(reservationRepository, never()).findByAdherentId(any());
        }

        @Test
        void adherent_avecFiltreStatut_neVoitQueSesReservations() {
            Reservation r1 = new Reservation();
            r1.setReservationId(1);
            r1.setAdherentId(ADHERENT1_ID);
            r1.setStatut(ReservationStatus.EN_ATTENTE);

            when(reservationRepository.findByAdherentIdAndStatut(ADHERENT1_ID, ReservationStatus.EN_ATTENTE))
                    .thenReturn(List.of(r1));

            List<ReservationResponse> result = reservationService.listerReservations(
                    ReservationStatus.EN_ATTENTE, ADHERENT1_ID, false);

            assertEquals(1, result.size());
            verify(reservationRepository).findByAdherentIdAndStatut(ADHERENT1_ID, ReservationStatus.EN_ATTENTE);
        }
    }

    // ================================================================
    // RG-05/RG-06 : Annulation avec vérification de statut
    // ================================================================
    @Nested
    class RG05RG06_Annulation {

        @Test
        void annuler_enAttente_doitReussir() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setStatut(ReservationStatus.EN_ATTENTE);
            reservation.setAdherentId(ADHERENT1_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.annulerReservation(1, ADHERENT1_ID, false);
            assertEquals(ReservationStatus.ANNULEE, response.getStatut());
        }

        @Test
        void annuler_disponible_doitReussir() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setStatut(ReservationStatus.DISPONIBLE);
            reservation.setAdherentId(ADHERENT1_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.annulerReservation(1, ADHERENT1_ID, false);
            assertEquals(ReservationStatus.ANNULEE, response.getStatut());
        }

        @Test
        void annuler_annulee_doitEchouer() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setStatut(ReservationStatus.ANNULEE);
            reservation.setAdherentId(ADHERENT1_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> reservationService.annulerReservation(1, ADHERENT1_ID, false));
            assertTrue(ex.getMessage().contains("RG-05") || ex.getMessage().contains("RG-06"));
        }

        @Test
        void annuler_expiree_doitEchouer() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setStatut(ReservationStatus.EXPIREE);
            reservation.setAdherentId(ADHERENT1_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            assertThrows(ConflictException.class,
                    () -> reservationService.annulerReservation(1, ADHERENT1_ID, false));
        }

        @Test
        void annuler_honoree_doitEchouer() {
            Reservation reservation = new Reservation();
            reservation.setReservationId(1);
            reservation.setStatut(ReservationStatus.HONOREE);
            reservation.setAdherentId(ADHERENT1_ID);
            when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

            assertThrows(ConflictException.class,
                    () -> reservationService.annulerReservation(1, ADHERENT1_ID, false));
        }
    }

    // ================================================================
    // Tests complémentaires — Validation et cas limites
    // ================================================================
    @Nested
    class CasLimites {

        @Test
        void creerReservation_livreInexistant_doitLancerNotFoundException() {
            when(booksRepository.findById(99)).thenReturn(Optional.empty());

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(99);

            assertThrows(NotFoundException.class,
                    () -> reservationService.creerReservation(request, ADHERENT1_ID, false));
        }

        @Test
        void creerReservation_adherentInexistant_doitLancerNotFoundException() {
            when(booksRepository.findById(LIVRE_INDISPONIBLE_ID)).thenReturn(Optional.of(livreIndisponible));
            when(usersRepository.findById(99)).thenReturn(Optional.empty());

            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);
            request.setAdherentId(99);

            assertThrows(NotFoundException.class,
                    () -> reservationService.creerReservation(request, 99, true));
        }

        @Test
        void creerReservation_livreIdManquant_doitLancerIllegalArgumentException() {
            ReservationRequest request = new ReservationRequest();
            request.setAdherentId(ADHERENT1_ID);

            assertThrows(IllegalArgumentException.class,
                    () -> reservationService.creerReservation(request, ADHERENT1_ID, false));
        }

        @Test
        void creerReservation_biblioSansAdherentId_doitLancerIllegalArgumentException() {
            ReservationRequest request = new ReservationRequest();
            request.setLivreId(LIVRE_INDISPONIBLE_ID);

            assertThrows(IllegalArgumentException.class,
                    () -> reservationService.creerReservation(request, BIBLIO_ID, true));
        }

        @Test
        void annulerReservation_inexistante_doitLancerNotFoundException() {
            when(reservationRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> reservationService.annulerReservation(99, ADHERENT1_ID, false));
        }

        @Test
        void supprimerReservation_inexistante_doitLancerNotFoundException() {
            when(reservationRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> reservationService.supprimerReservation(99));
        }
    }
}
