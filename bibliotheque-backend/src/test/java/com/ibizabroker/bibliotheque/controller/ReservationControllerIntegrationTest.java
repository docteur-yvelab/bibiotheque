package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.JwtRequest;
import com.ibizabroker.bibliotheque.entity.JwtResponse;
import com.ibizabroker.bibliotheque.service.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du module Réservation (RS-01/03/04/05).
 *
 * Profil "test" : base H2 en mémoire, aucune PostgreSQL requise.
 * Authentification par JWT réel via JwtService + comptes seedés
 * par TestDataInitializer (adherent1 a une réservation sur Clean Code).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BooksRepository booksRepository;

    private String tokenAdherent1;
    private String tokenAdherent2;
    private String tokenBiblio;

    @BeforeAll
    void obtenirTokens() throws Exception {
        tokenAdherent1 = authenticate("adherent1", "adherent1");
        tokenAdherent2 = authenticate("adherent2", "adherent2");
        tokenBiblio = authenticate("biblio1", "biblio1");
    }

    private String authenticate(String username, String password) throws Exception {
        JwtRequest request = new JwtRequest();
        request.setUserName(username);
        request.setUserPassword(password);
        JwtResponse response = jwtService.createJwtToken(request);
        return response.getJwtToken();
    }

    // ========================================================
    // RS-01 : 401 sans token
    // ========================================================

    @Test
    void getReservations_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================
    // RS-05 : un ADHERENT ne voit que les siennes
    // ========================================================

    @Test
    void getReservations_avecTokenAdherent_doitRenvoyer200() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getReservations_adherentNeVoitQueLesSiennes_RS05() throws Exception {
        // adherent2 n'a aucune réservation : la liste doit être vide,
        // même si les données globales (seed + création ci-dessous) existent
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ========================================================
    // RS-03 : réservation d'autrui -> 403 (jamais 401)
    // ========================================================

    @Test
    void getReservation_dunAutreAdherent_doitRenvoyer403() throws Exception {
        Integer reservationId = premiereReservationDe("adherent1");

        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + tokenAdherent2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void patchAnnuler_reservationDunAutreAdherent_doitRenvoyer403_RS03() throws Exception {
        Integer reservationId = premiereReservationDe("adherent1");

        mockMvc.perform(patch("/api/reservations/" + reservationId + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdherent2))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReservation_saPropreReservation_doitReussir_RS03() throws Exception {
        Integer reservationId = premiereReservationDe("adherent1");

        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId));
    }

    // ========================================================
    // RS-04 : l'identité POST vient du token, pas du body
    // ========================================================

    @Test
    void postReservations_adherentAvecAdherentIdFalsifie_doitEtreCreePourLui_RS04() throws Exception {
        // adherent2 (userId != adherent1) tente de créer pour adherent1 :
        // le body adherentId est ignoré, la réservation doit être à SON nom
        Integer idAdherent2 = idDe("adherent2");
        Integer livre = creerLivreIndisponible("Test-RS04-Forgery");

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent2)
                        .contentType("application/json")
                        .content("{\"livreId\":" + livre + ",\"adherentId\":" + idAdherent1() + "}"))
                .andExpect(status().isCreated())
                // RS-04 : la réservation doit appartenir à l'utilisateur du token
                .andExpect(jsonPath("$.adherentId").value(idAdherent2));
    }

    @Test
    void postReservations_bibliothecairePourUnTiers_doitReussir_RS04() throws Exception {
        // Le BIBLIOTHECAIRE crée pour adherent2 : adherentId du body respecté.
        // Livre frais : indépendant de l'ordre d'exécution des autres tests
        // (sinon RG-02 répondrait 409 si adherent2 a déjà réservé Clean Code)
        Integer livre = creerLivreIndisponible("Test-RS04-Biblio");

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenBiblio)
                        .contentType("application/json")
                        .content("{\"livreId\":" + livre + ",\"adherentId\":" + idDe("adherent2") + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(idDe("adherent2")));
    }

    @Test
    void postReservations_bibliothecaireSansAdherentId_doitRenvoyer400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenBiblio)
                        .contentType("application/json")
                        .content("{\"livreId\":" + livreIndisponibleId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("adherentId")));
    }

    // ========================================================
    // RG-01 : livre disponible -> 409 avec message nommant la règle
    // ========================================================

    @Test
    void postReservations_livreDisponible_doitRenvoyer409_RG01() throws Exception {
        Integer livreDispo = livreDisponibleId();

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent2)
                        .contentType("application/json")
                        .content("{\"livreId\":" + livreDispo + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("RG-01")));
    }

    // ========================================================
    // 404 : ressources inexistantes
    // ========================================================

    @Test
    void getReservation_inexistante_doitRenvoyer404() throws Exception {
        mockMvc.perform(get("/api/reservations/99999")
                        .header("Authorization", "Bearer " + tokenBiblio))
                .andExpect(status().isNotFound());
    }

    // ========================================================
    // Helpers
    // ========================================================

    /** Id de la première réservation de l'adhérent donné (via l'API). */
    private Integer premiereReservationDe(String username) throws Exception {
        String token = "adherent1".equals(username) ? tokenAdherent1 : tokenAdherent2;
        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.isBlank(),
                username + " doit avoir au moins une réservation seedée");
        return com.jayway.jsonpath.JsonPath.read(body, "$[0].reservationId");
    }

    /** userId d'un compte seedé (lecture SQL directe sur H2). */
    private Integer idDe(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE username = ?", Integer.class, username);
    }

    private Integer idAdherent1() {
        return idDe("adherent1");
    }

    /** Id du livre indisponible seedé « Clean Code » (noOfCopies = 0). */
    private Integer livreIndisponibleId() {
        return jdbcTemplate.queryForObject(
                "SELECT book_id FROM books WHERE book_name = 'Clean Code'", Integer.class);
    }

    /** Id du livre disponible seedé « Effective Java » (noOfCopies = 3). */
    private Integer livreDisponibleId() {
        return jdbcTemplate.queryForObject(
                "SELECT book_id FROM books WHERE book_name = 'Effective Java'", Integer.class);
    }

    /**
     * Crée un livre indisponible dédié au test (nom unique) pour rendre
     * les tests POST indépendants de l'ordre d'exécution et du seed.
     * Passe par JPA pour que l'ID soit généré par la séquence Hibernate.
     */
    private Integer creerLivreIndisponible(String nom) {
        Books livre = new Books();
        livre.setBookName(nom + "-" + System.nanoTime());
        livre.setBookAuthor("Test");
        livre.setBookGenre("Test");
        livre.setNoOfCopies(0);
        return booksRepository.save(livre).getBookId();
    }
}
