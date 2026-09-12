package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.service.JwtService;
import com.ibizabroker.bibliotheque.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration du module Réservation (Séance 4).
 *
 * Pile HTTP complète (filtres Spring Security + JWT réels) sur base H2
 * en mémoire — aucune PostgreSQL de dev requise. Les comptes utilisés
 * sont créés par TestDataInitializer (idempotent) : adherent1, adherent2,
 * biblio1 (voir TESTING.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BooksRepository booksRepository;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Génère un vrai JWT signé pour un compte existant. */
    private String tokenPour(String username) {
        UserDetails userDetails = jwtService.loadUserByUsername(username);
        return jwtUtil.generateToken(userDetails);
    }

    private Integer userId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE username = ?", Integer.class, username);
    }

    private Integer premiereReservationDe(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT reservation_id FROM reservation WHERE adherent_id = ? LIMIT 1",
                Integer.class, userId(username));
    }

    /** Crée un livre unique par test pour garantir l'indépendance des scénarios. */
    private Books creerLivre(String nom, int copies) {
        Books livre = new Books();
        livre.setBookName(nom + " " + System.nanoTime());
        livre.setBookAuthor("Auteur Test");
        livre.setBookGenre("Test");
        livre.setNoOfCopies(copies);
        return booksRepository.save(livre);
    }

    private String corpsReservation(Integer livreId, Integer adherentId) {
        String champAdherent = adherentId == null ? "null" : adherentId.toString();
        return "{\"livreId\": " + livreId + ", \"adherentId\": " + champAdherent + "}";
    }

    /** Lecture JSON robuste (Jackson), indépendante du provider json-path. */
    private Integer jsonInt(MvcResult resultat, String champ) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(resultat.getResponse().getContentAsString())
                .path(champ).asInt();
    }

    private String jsonText(MvcResult resultat, String champ) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(resultat.getResponse().getContentAsString())
                .path(champ).asText();
    }

    // ------------------------------------------------------------------
    // RS-01 : authentification exigée
    // ------------------------------------------------------------------

    @Test
    void listerReservations_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creerReservation_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsReservation(1, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consulterAvecTokenMalforme_doitRenvoyer401_pas500() throws Exception {
        // Régression : un token illisible levait MalformedJwtException, non
        // attrapée par le filtre -> 500 au lieu du 401 exigé par RS-01.
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer token.totalement.invalide"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // RS-05 : filtrage par rôle sur la liste
    // ------------------------------------------------------------------

    @Test
    void listerReservations_avecTokenAdherent_doitRenvoyer200() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listerReservations_adherentAvecFiltreFalsifie_neVoitQueLesSiennes() throws Exception {
        // adherent1 tente de lire les réservations d'adherent2 via query param :
        // le serveur ignore le paramètre (RS-05) — la liste ne contient
        // aucune réservation d'un autre adhérent.
        Integer adherent2Id = userId("adherent2");

        MvcResult resultat = mockMvc.perform(get("/api/reservations")
                        .param("adherentId", adherent2Id.toString())
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String corps = resultat.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(
                corps.contains("\"adherentId\":" + adherent2Id),
                "Un ADHERENT ne doit jamais voir les réservations d'un autre (RS-05).");
    }

    @Test
    void listerReservations_avecTokenBibliothecaire_doitRenvoyer200() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("biblio1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ------------------------------------------------------------------
    // RS-03 : propriété des réservations
    // ------------------------------------------------------------------

    @Test
    void consulterReservationDeAutreAdherent_doitRenvoyer403() throws Exception {
        Integer reservationAdherent2 = premiereReservationDe("adherent2");

        mockMvc.perform(get("/api/reservations/" + reservationAdherent2)
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void annulerReservationDeAutreAdherent_doitRenvoyer403() throws Exception {
        Integer reservationAdherent2 = premiereReservationDe("adherent2");

        mockMvc.perform(patch("/api/reservations/" + reservationAdherent2 + "/annuler")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // RS-02 : DELETE réservé au BIBLIOTHECAIRE
    // ------------------------------------------------------------------

    @Test
    void supprimerReservation_enTantQuAdherent_doitRenvoyer403() throws Exception {
        Integer reservationAdherent1 = premiereReservationDe("adherent1");

        mockMvc.perform(delete("/api/reservations/" + reservationAdherent1)
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supprimerReservation_enTantQueBibliothecaire_doitRenvoyer204() throws Exception {
        // Scénario autonome : le bibliothécaire crée une réservation pour un
        // tiers puis la supprime.
        Books livre = creerLivre("Livre Suppression Biblio", 0);
        Integer adherent2Id = userId("adherent2");

        MvcResult creation = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("biblio1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsReservation(livre.getBookId(), adherent2Id)))
                .andExpect(status().isCreated())
                .andReturn();

        String reservationId = jsonInt(creation, "reservationId").toString();

        mockMvc.perform(delete("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + tokenPour("biblio1")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // RS-04 : l'identité d'un ADHERENT vient du token
    // ------------------------------------------------------------------

    @Test
    void creerReservation_adherentAvecIdentiteFalsifiee_doitEtreCreePourLuiMeme() throws Exception {
        Books livre = creerLivre("Livre RS04 Adherent", 0);
        Integer adherent1Id = userId("adherent1");
        Integer adherent2Id = userId("adherent2");

        // adherent1 prétend être adherent2 dans le body : le serveur doit
        // ignorer le champ et créer pour adherent1 (RS-04).
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsReservation(livre.getBookId(), adherent2Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(adherent1Id));
    }

    @Test
    void creerReservation_bibliothecairePourUnTiers_doitReussir() throws Exception {
        Books livre = creerLivre("Livre Biblio Tiers", 0);
        Integer adherent2Id = userId("adherent2");

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("biblio1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsReservation(livre.getBookId(), adherent2Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(adherent2Id));
    }

    // ------------------------------------------------------------------
    // Règles métier à travers la pile HTTP
    // ------------------------------------------------------------------

    @Test
    void creerReservation_surLivreDisponible_doitRenvoyer409() throws Exception {
        Books livreDisponible = creerLivre("Livre Disponible RG01", 2);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsReservation(livreDisponible.getBookId(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("RG-01")));
    }

    @Test
    void creerReservation_sansLivreId_doitRenvoyer400AvecMessagePrecis() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adherentId\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("livreId")));
    }

    @Test
    void supprimerReservationInconnue_enTantQueBibliothecaire_doitRenvoyer404() throws Exception {
        mockMvc.perform(delete("/api/reservations/999999")
                        .header("Authorization", "Bearer " + tokenPour("biblio1")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Non-régression : /authenticate reste fonctionnel
    // ------------------------------------------------------------------

    @Test
    void authentification_avecCompteSeede_doitProduireUnTokenUtilisable() throws Exception {
        MvcResult authentification = mockMvc.perform(post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"adherent1\", \"password\": \"adherent1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = jsonText(authentification, "jwtToken");

        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
