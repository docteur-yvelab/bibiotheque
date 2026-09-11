package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.CurrentUserService;
import com.ibizabroker.bibliotheque.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Réservations", description = "Gestion des réservations de livres")
@CrossOrigin("http://localhost:4200/")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CurrentUserService currentUserService;

    // ================================================================
    // POST /api/reservations — Créer une réservation
    // RS-04 : L'identité de l'adhérent est extraite du token JWT,
    //         PAS du corps de la requête. Le BIBLIOTHECAIRE peut
    //         créer pour n'importe qui.
    // ================================================================
    @Operation(summary = "Créer une réservation",
            description = "Crée une nouvelle réservation. " +
                    "ADHERENT : crée pour lui-même (ignoré adherentId du body). " +
                    "BIBLIOTHECAIRE : crée pour n'importe quel adhérent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Accès refusé"),
            @ApiResponse(responseCode = "400", description = "Champ(s) obligatoire(s) manquant(s)"),
            @ApiResponse(responseCode = "404", description = "Livre ou adherent non trouvé"),
            @ApiResponse(responseCode = "409", description = "Règle de gestion violée")
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
        Integer userId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.creerReservation(request, userId, isBiblio);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ================================================================
    // GET /api/reservations — Lister les réservations
    // RS-05 : ADHERENT ne voit QUE ses réservations.
    //         BIBLIOTHECAIRE voit toutes les réservations.
    // ================================================================
    @Operation(summary = "Lister les réservations",
            description = "ADHERENT : voit ses réservations uniquement. " +
                    "BIBLIOTHECAIRE : voit toutes les réservations. Filtrable par statut.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste retournée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listerReservations(
            @Parameter(description = "Filtrer par statut")
            @RequestParam(required = false) ReservationStatus statut) {

        Integer userId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        List<ReservationResponse> reservations = reservationService.listerReservations(statut, userId, isBiblio);
        return ResponseEntity.ok(reservations);
    }

    // ================================================================
    // GET /api/reservations/{id} — Consulter une réservation
    // RS-03 : ADHERENT ne peut consulter que SES réservations.
    //         BIBLIOTHECAIRE peut consulter toutes les réservations.
    // ================================================================
    @Operation(summary = "Consulter une réservation",
            description = "ADHERENT : consulte ses réservations uniquement. " +
                    "BIBLIOTHECAIRE : consulte toutes les réservations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation trouvée",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Accès refusé —cette réservation ne vous appartient pas"),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> consulterReservation(@PathVariable Integer id) {
        Integer userId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.consulterReservation(id, userId, isBiblio);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // PATCH /api/reservations/{id}/annuler — Annuler une réservation
    // RS-03 : ADHERENT ne peut annuler que SES réservations.
    //         BIBLIOTHECAIRE peut annuler n'importe quelle réservation.
    // ================================================================
    @Operation(summary = "Annuler une réservation",
            description = "ADHERENT : annule ses réservations uniquement. " +
                    "BIBLIOTHECAIRE : annule n'importe quelle réservation. " +
                    "Seules les réservations EN_ATTENTE ou DISPONIBLE peuvent être annulées.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Accès refusé — réservation ne vous appartient pas"),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée"),
            @ApiResponse(responseCode = "409", description = "Statut incompatible")
    })
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        Integer userId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.annulerReservation(id, userId, isBiblio);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // DELETE /api/reservations/{id} — Supprimer une réservation
    // RS-02 : Seul le BIBLIOTHECAIRE peut supprimer.
    //         (déjà filtré par Spring Security .hasRole("BIBLIOTHECAIRE"))
    // ================================================================
    @Operation(summary = "Supprimer une réservation",
            description = "Réservé au BIBLIOTHECAIRE uniquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Réservation supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Accès refusé — BIBLIOTHECAIRE uniquement"),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        // RS-02 : Spring Security a déjà vérifié hasRole("BIBLIOTHECAIRE")
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}
