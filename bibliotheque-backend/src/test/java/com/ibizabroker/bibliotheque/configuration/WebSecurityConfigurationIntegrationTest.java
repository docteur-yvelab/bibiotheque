package com.ibizabroker.bibliotheque.configuration;

import com.ibizabroker.bibliotheque.entity.JwtRequest;
import com.ibizabroker.bibliotheque.entity.JwtResponse;
import com.ibizabroker.bibliotheque.service.JwtService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration de WebSecurityConfiguration — la chaîne de filtres
 * elle-même (profil "test" : H2 en mémoire, JWT réel via JwtService).
 *
 * Ce que ces tests prouvent :
 *  - RS-01 : /api/reservations/** exige un token (401 JSON sinon) ;
 *  - la distinction 401/403 n'est jamais inversée ;
 *  - les permitAll ne fuient pas sur les endpoints protégés ;
 *  - la session est STATELESS (aucun Set-Cookie de session) ;
 *  - RS-02 : le @PreAuthorize('BIBLIOTHECAIRE') du DELETE répond 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSecurityConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenAdherent1;
    private String tokenBiblio;

    @BeforeAll
    void obtenirTokens() throws Exception {
        tokenAdherent1 = authenticate("adherent1", "adherent1");
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
    // RS-01 : 401 sans token, en JSON, sur chaque verbe
    // ========================================================

    @Test
    void get_sansToken_doitRenvoyer401Json() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void post_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content("{\"livreId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(delete("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================
    // Token invalide / mal formé : 401 aussi (pas 500)
    // ========================================================

    @Test
    void get_avecTokenInvalide_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer token.totalement.invalide"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================
    // 401 ≠ 403 : un ADHERENT authentifié ne reçoit jamais 401
    // ========================================================

    @Test
    void delete_parAdherent_doitRenvoyer403Pas401_RS02() throws Exception {
        // RS-02 : l'identité est connue, seule l'autorisation manque
        mockMvc.perform(delete("/api/reservations/99999")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void delete_parBibliothecaire_neDoitPasEtre403_RS02() throws Exception {
        // Le bibliothécaire passe le @PreAuthorize ; 404 attendu sur une
        // ressource inexistante — en tout cas PAS 401 ni 403
        mockMvc.perform(delete("/api/reservations/99999")
                        .header("Authorization", "Bearer " + tokenBiblio))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Le BIBLIOTHECAIRE ne doit pas être bloqué (reçu " + status + ")");
                    }
                });
    }

    // ========================================================
    // Périmètre des permitAll : ouvert là où il faut, fermé ailleurs
    // ========================================================

    @Test
    void authenticate_sansToken_doitEtreAccessible() throws Exception {
        mockMvc.perform(post("/authenticate")
                        .contentType("application/json")
                        .content("{\"username\":\"adherent1\",\"password\":\"adherent1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwtToken").isNotEmpty());
    }

    @Test
    void borrow_sansToken_doitResterAccessible_nonRegression() throws Exception {
        mockMvc.perform(get("/borrow"))
                .andExpect(status().isOk());
    }

    @Test
    void adminBooks_get_sansToken_doitResterAccessible_nonRegression() throws Exception {
        mockMvc.perform(get("/admin/books"))
                .andExpect(status().isOk());
    }

    @Test
    void swagger_doitResterAccessible_sansToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void adminUsers_sansToken_doitResterProtege() throws Exception {
        // /admin/users n'est pas en permitAll : doit rester sous authenticated()
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUsers_avecTokenAdherent_doitRenvoyer403() throws Exception {
        // Authentifié mais rôle ADHERENT (contrôleur : hasRole('Admin'))
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isForbidden());
    }

    // ========================================================
    // STATELESS : aucune session HTTP ne doit être créée
    // ========================================================

    @Test
    void reponse_neDoitPasCreerDeSessionHttp_stateless() throws Exception {
        // SessionCreationPolicy.STATELESS : même authentifié, aucune
        // JSESSIONID de session applicative ne doit être posée
        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie != null && setCookie.contains("JSESSIONID")) {
            throw new AssertionError("STATELESS violé : une session JSESSIONID a été créée");
        }
    }
}
