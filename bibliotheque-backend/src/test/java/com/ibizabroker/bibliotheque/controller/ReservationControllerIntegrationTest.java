package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.JwtRequest;
import com.ibizabroker.bibliotheque.entity.JwtResponse;
import com.ibizabroker.bibliotheque.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration RS-01/RS-03/RS-05 sur GET /api/reservations.
 *
 * Profil "test" : base H2 en mémoire, aucune PostgreSQL requise.
 * L'authentification passe par le vrai /authenticate (JWT réel),
 * avec les comptes seedés par TestDataInitializer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenAdherent1;
    private String tokenAdherent2;

    @BeforeEach
    void obtenirTokens() throws Exception {
        tokenAdherent1 = authenticate("adherent1", "adherent1");
        tokenAdherent2 = authenticate("adherent2", "adherent2");
    }

    private String authenticate(String username, String password) throws Exception {
        JwtRequest request = new JwtRequest();
        request.setUserName(username);
        request.setUserPassword(password);
        JwtResponse response = jwtService.createJwtToken(request);
        return response.getJwtToken();
    }

    /**
     * Cas 1 — RS-01 : sans header Authorization -> 401.
     */
    @Test
    void getReservations_sansToken_doitRenvoyer401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Cas 2 — RS-01/RS-05 : un ADHERENT authentifié reçoit 200.
     */
    @Test
    void getReservations_avecTokenAdherent_doitRenvoyer200() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Cas 3 — RS-03 : un ADHERENT qui consulte la réservation d'un autre
     * adhérent reçoit 403 (pas 401 : on sait qui il est).
     */
    @Test
    void getReservation_dunAutreAdherent_doitRenvoyer403() throws Exception {
        // La réservation seedée appartient à adherent1
        MvcResult result = mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer " + tokenAdherent1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.isBlank(), "adherent1 doit avoir au moins une réservation seedée");
        com.jayway.jsonpath.JsonPath.parse(body);
        Integer reservationId = com.jayway.jsonpath.JsonPath.read(body, "$[0].reservationId");

        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + tokenAdherent2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
