package com.ibizabroker.bibliotheque.configuration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de la configuration de sécurité elle-même.
 *
 * Vérifie le contrat de WebSecurityConfiguration sur la pile HTTP réelle :
 *  - 401 sur tous les verbes sans token (RS-01), y compris token malformé
 *    ou expiré — jamais un 500 ;
 *  - 403 (et pas 401) pour une identité connue sans droits (RS-02) ;
 *  - périmètre permitAll exact (authenticate, /admin/books en lecture,
 *    Swagger, /borrow) ;
 *  - session STATELESS : aucun JSESSIONID n'est jamais émis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSecurityConfigurationIntegrationTest {

    /** Même secret que JwtUtil — utilisé uniquement pour forger un token expiré. */
    private static final String SECRET_KEY = "learn_programming_yourself_learn_programming_yourself";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.ibizabroker.bibliotheque.util.JwtUtil jwtUtil;

    @Autowired
    private com.ibizabroker.bibliotheque.service.JwtService jwtService;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String tokenPour(String username) {
        UserDetails userDetails = jwtService.loadUserByUsername(username);
        return jwtUtil.generateToken(userDetails);
    }

    private String tokenExpire() {
        SecretKey cle = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("adherent1")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(cle)
                .compact();
    }

    // ------------------------------------------------------------------
    // RS-01 : 401 sur tous les verbes sans token
    // ------------------------------------------------------------------

    @Test
    void getReservations_sansToken_doitRenvoyer401AvecMessageJson() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void postReservations_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchAnnuler_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/annuler"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReservation_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(delete("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 401 vs 500 : tokens illisibles ou expirés restent des 401
    // ------------------------------------------------------------------

    @Test
    void getReservations_avecTokenMalforme_doitRenvoyer401_pas500() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReservations_avecTokenExpire_doitRenvoyer401_pas500() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenExpire()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReservations_avecSchemeNonBearer_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Basic " + tokenPour("adherent1")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // RS-02 : 403 (et pas 401) pour une identité connue sans droits
    // ------------------------------------------------------------------

    @Test
    void deleteReservation_parAdherent_doitRenvoyer403_pas401() throws Exception {
        mockMvc.perform(delete("/api/reservations/1")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUsers_parAdherent_doitRenvoyer403() throws Exception {
        // /admin/users n'est pas en permitAll : un ADHERENT authentifié
        // reçoit 403, ce qui prouve que le périmètre public est bien ciblé.
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReservation_parBibliothecaire_depasseLaSecuriteAvec404() throws Exception {
        // Le @PreAuthorize laisse passer le BIBLIOTHECAIRE : la requête
        // atteint le contrôleur et échoue en 404 sur l'id inconnu —
        // preuve que la méthode sécurisée est bien exécutée.
        mockMvc.perform(delete("/api/reservations/999999")
                        .header("Authorization", "Bearer " + tokenPour("biblio1")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Périmètre permitAll : non-régression des routes publiques
    // ------------------------------------------------------------------

    @Test
    void authenticate_avecIdentifiantsValides_doitRenvoyer200AvecToken() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"adherent1\", \"password\": \"adherent1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwtToken").exists());
    }

    @Test
    void authenticate_avecIdentifiantsInvalides_neDoitPasEtreUn500() throws Exception {
        int statut = mockMvc.perform(post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"adherent1\", \"password\": \"mauvais\"}"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(
                statut == 401 || statut == 403 || statut == 400,
                "Un échec d'authentification ne doit jamais être un 500, reçu : " + statut);
    }

    @Test
    void getAdminBooks_anonyme_doitResterPublic() throws Exception {
        mockMvc.perform(get("/admin/books"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerDocs_anonyme_doiventEtreAccessibles() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // STATELESS : aucun JSESSIONID n'est jamais émis
    // ------------------------------------------------------------------

    @Test
    void reponseAvecToken_valide_neDoitPasEmettreDeJsessionid() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isOk())
                .andReturn();

        org.junit.jupiter.api.Assertions.assertNull(
                resultat.getResponse().getCookie("JSESSIONID"),
                "L'API est stateless : aucune session ne doit être créée.");
    }

    // ------------------------------------------------------------------
    // Le token d'adherent1 donne bien accès à ses données (contrat complet)
    // ------------------------------------------------------------------

    @Test
    void getReservations_avecTokenAdherentValide_doitRenvoyer200() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenPour("adherent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
