package com.ibizabroker.bibliotheque.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.entity.JwtRequest;
import com.ibizabroker.bibliotheque.entity.JwtResponse;
import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration pour les endpoints REST /api/reservations.
 * Valide les règles RS-01 à RS-05 avec de vrais appels HTTP.
 *
 * Prérequis : une base PostgreSQL de test en cours d'exécution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private BooksRepository booksRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String tokenAdherent1;
    private String tokenAdherent2;
    private String tokenBibliothecaire;

    @BeforeAll
    void setup() throws Exception {
        // Forcer les noms de rôles corrects (au cas où la BDD a les anciens noms)
        jdbcTemplate.update("UPDATE role SET role_name = 'BIBLIOTHECAIRE' WHERE role_id = 1");
        jdbcTemplate.update("UPDATE role SET role_name = 'ADHERENT' WHERE role_id = 2");

        reservationRepository.deleteAll();
        booksRepository.findById(1).ifPresent(book -> {
            if (book.getNoOfCopies() == 0) {
                book.setNoOfCopies(1);
                booksRepository.save(book);
            }
        });
        tokenAdherent1 = authenticate("A1", "123456");
        tokenAdherent2 = authenticate("A2", "123456");
        tokenBibliothecaire = authenticate("admin", "123456");
    }

    /** Nettoyer les réservations entre chaque test pour éviter les collisions RG-02 */
    @BeforeEach
    void cleanReservations() {
        reservationRepository.deleteAll();
    }

    private String authenticate(String username, String password) throws Exception {
        JwtRequest req = new JwtRequest();
        req.setUsername(username);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JwtResponse jwt = objectMapper.readValue(
                result.getResponse().getContentAsString(), JwtResponse.class);
        return jwt.getJwtToken();
    }

    /** Créer une réservation et retourner le corps de la réponse. */
    private ReservationResponse createAndGetResponse(String token, int livreId, int adherentId) throws Exception {
        ReservationRequest req = new ReservationRequest();
        req.setLivreId(livreId);
        req.setAdherentId(adherentId);
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ReservationResponse.class);
    }

    // ================================================================
    // RS-01 : Sans token JWT → 401 Unauthorized
    // ================================================================

    @Test @Order(1)
    @DisplayName("RS-01 : GET /api/reservations sans token → 401")
    void rs01_get_sansToken() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    @DisplayName("RS-01 : POST /api/reservations sans token → 401")
    void rs01_post_sansToken() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(3)
    @DisplayName("RS-01 : GET /api/reservations/{id} sans token → 401")
    void rs01_getById_sansToken() throws Exception {
        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(4)
    @DisplayName("RS-01 : PATCH /api/reservations/{id}/annuler sans token → 401")
    void rs01_patch_sansToken() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/annuler"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(5)
    @DisplayName("RS-01 : DELETE /api/reservations/{id} sans token → 401")
    void rs01_delete_sansToken() throws Exception {
        mockMvc.perform(delete("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(6)
    @DisplayName("RS-01 : Header Bearer vide → 401")
    void rs01_bearerVide() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(7)
    @DisplayName("RS-01 : Header Authorization sans Bearer → 401")
    void rs01_headerSansBearer() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // RS-02 : ADHERENT → 403 sur DELETE
    // ================================================================

    @Test @Order(10)
    @DisplayName("RS-02 : DELETE réservation par ADHERENT → 403")
    void rs02_adherentSupprime403() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 2, 2);

        mockMvc.perform(delete("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isForbidden());
    }

    @Test @Order(11)
    @DisplayName("RS-02 : DELETE réservation par BIBLIOTHECAIRE → 204")
    void rs02_bibliothecaireSupprimeOk() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 3, 3);

        mockMvc.perform(delete("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isNoContent());
    }

    @Test @Order(12)
    @DisplayName("RS-02 : DELETE réservation inexistante → 404")
    void rs02_deleteInexistante404() throws Exception {
        mockMvc.perform(delete("/api/reservations/99999")
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // RS-03 : ADHERENT ne peut pas accéder aux réservations d'un autre
    // ================================================================

    @Test @Order(20)
    @DisplayName("RS-03 : GET réservation d'un autre adhérent → 403")
    void rs03_consulterReservationAutrui403() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 4, 3);

        mockMvc.perform(get("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isForbidden());
    }

    @Test @Order(21)
    @DisplayName("RS-03 : GET sa propre réservation → 200")
    void rs03_consulterSaPropreReservation200() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 5, 3);

        mockMvc.perform(get("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenAdherent2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherentId").value(3));
    }

    @Test @Order(22)
    @DisplayName("RS-03 : ANNULER réservation d'un autre → 403")
    void rs03_annulerReservationAutrui403() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 2, 3);

        mockMvc.perform(patch("/api/reservations/" + resp.getReservationId() + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isForbidden());
    }

    @Test @Order(23)
    @DisplayName("RS-03 : ANNULER sa propre réservation → 200")
    void rs03_annulerSaPropreReservation200() throws Exception {
        ReservationResponse resp = createAndGetResponse(tokenBibliothecaire, 3, 2);

        mockMvc.perform(patch("/api/reservations/" + resp.getReservationId() + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    // ================================================================
    // RS-04 : Identité du token, pas du body
    // ================================================================

    @Test @Order(30)
    @DisplayName("RS-04 : ADHERENT envoie un autre adherentId → le body est ignoré")
    void rs04_adherentIgnoreBody() throws Exception {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(4);
        request.setAdherentId(3); // tentative de falsification

        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ReservationResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReservationResponse.class);
        Assertions.assertEquals(2, resp.getAdherentId(),
                "RS-04 : L'identité doit venir du token JWT, pas du body");

        mockMvc.perform(delete("/api/reservations/" + resp.getReservationId())
                .header("Authorization", "Bearer " + tokenBibliothecaire));
    }

    @Test @Order(31)
    @DisplayName("RS-04 : BIBLIOTHECAIRE crée pour n'importe qui")
    void rs04_biblioCreePourAutrui() throws Exception {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(5);
        request.setAdherentId(4); // A3

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenBibliothecaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(4));
    }

    @Test @Order(32)
    @DisplayName("RS-04 : BIBLIOTHECAIRE sans adherentId → 400")
    void rs04_biblioSansAdherentId400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenBibliothecaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\":2}"))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // RS-05 : GET filtré automatiquement pour l'ADHERENT
    // ================================================================

    @Test @Order(40)
    @DisplayName("RS-05 : ADHERENT ne voit que ses réservations")
    void rs05_adherentNeVoitQueLesSiennes() throws Exception {
        createAndGetResponse(tokenAdherent1, 2, 2);
        createAndGetResponse(tokenAdherent1, 4, 2);

        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andReturn();

        ReservationResponse[] reservations = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReservationResponse[].class);

        for (ReservationResponse r : reservations) {
            Assertions.assertEquals(2, r.getAdherentId(),
                    "RS-05 : A1 ne doit voir que ses réservations");
        }
    }

    @Test @Order(41)
    @DisplayName("RS-05 : BIBLIOTHECAIRE voit toutes les réservations")
    void rs05_biblioVoitTout() throws Exception {
        // Créer des réservations pour A1 et A2
        createAndGetResponse(tokenAdherent1, 2, 2);
        createAndGetResponse(tokenAdherent2, 3, 3);

        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isOk())
                .andReturn();

        ReservationResponse[] reservations = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReservationResponse[].class);
        // Le BIBLIOTHECAIRE doit voir les 2 réservations (de A1 et A2)
        Assertions.assertTrue(reservations.length >= 2,
                "Le BIBLIOTHECAIRE doit voir toutes les réservations");
    }

    @Test @Order(42)
    @DisplayName("RS-05 : Filtrage par statut ne montre que les réservations de l'ADHERENT")
    void rs05_filtreStatutAdherent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .param("statut", "EN_ATTENTE")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andReturn();

        ReservationResponse[] reservations = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReservationResponse[].class);

        for (ReservationResponse r : reservations) {
            Assertions.assertEquals(2, r.getAdherentId());
            Assertions.assertEquals("EN_ATTENTE", r.getStatut().name());
        }
    }

    // ================================================================
    // CRUD complet + cas limites
    // ================================================================

    @Test @Order(50)
    @DisplayName("CRUD complet : CREATE → GET → ANNULER → DELETE → GET 404")
    void crud_cycleComplet() throws Exception {
        // CREATE
        ReservationRequest createReq = new ReservationRequest();
        createReq.setLivreId(2);
        createReq.setAdherentId(2);

        MvcResult created = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andReturn();

        ReservationResponse resp = objectMapper.readValue(
                created.getResponse().getContentAsString(), ReservationResponse.class);

        // GET
        mockMvc.perform(get("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk());

        // ANNULER
        mockMvc.perform(patch("/api/reservations/" + resp.getReservationId() + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));

        // DELETE
        mockMvc.perform(delete("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isNoContent());

        // GET → 404
        mockMvc.perform(get("/api/reservations/" + resp.getReservationId())
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isNotFound());
    }

    @Test @Order(51)
    @DisplayName("RG-01 : Réservation d'un livre disponible → 409")
    void rg01_livreDisponible409() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("RG-01")));
    }

    @Test @Order(52)
    @DisplayName("Livre inexistant → 404")
    void livreInexistant404() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\":9999}"))
                .andExpect(status().isNotFound());
    }

    @Test @Order(53)
    @DisplayName("GET réservation inexistante → 404")
    void getReservationInexistante404() throws Exception {
        mockMvc.perform(get("/api/reservations/99999")
                        .header("Authorization", "Bearer " + tokenBibliothecaire))
                .andExpect(status().isNotFound());
    }

    @Test @Order(54)
    @DisplayName("POST sans livreId → 400")
    void postSansLivreId400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
