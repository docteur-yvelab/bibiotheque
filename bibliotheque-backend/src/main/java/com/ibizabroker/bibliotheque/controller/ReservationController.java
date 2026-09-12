package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints du module Réservation.
 *
 * RS-01 : aucun de ces endpoints n'est en permitAll() — un token JWT
 * valide est exigé (401 sinon, géré par JwtAuthenticationEntryPoint).
 * RS-02 : DELETE est réservé au BIBLIOTHECAIRE via @PreAuthorize ; un
 * ADHERENT qui l'appelle reçoit 403 (géré par JsonAccessDeniedHandler).
 * Les vérifications de propriété (RS-03) et d'identité (RS-04) sont
 * dans ReservationService, via SecurityUtils.
 */
@Tag(name = "Réservations", description = "Gestion des réservations de livres (RG-01 à RG-06, RS-01 à RS-05)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    /**
     * POST /api/reservations — 201.
     * ADHERENT : pour lui-même uniquement (adherentId du body ignoré, RS-04).
     * BIBLIOTHECAIRE : pour n'importe qui (adherentId du body obligatoire).
     */
    @Operation(summary = "Créer une réservation",
            description = "ADHERENT : réservé pour lui-même (adherentId du body ignoré, RS-04). "
                    + "BIBLIOTHECAIRE : pour n'importe quel adhérent (adherentId obligatoire). "
                    + "Règles RG-01 (livre indisponible), RG-02 (une seule réservation active par livre), "
                    + "RG-03 (max 3 actives), RG-04 (expiration +7 jours).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "livreId manquant (ou adherentId manquant pour un BIBLIOTHECAIRE)"),
            @ApiResponse(responseCode = "401", description = "Token absent/invalide"),
            @ApiResponse(responseCode = "404", description = "Livre ou adhérent inexistant"),
            @ApiResponse(responseCode = "409", description = "RG-01 (livre disponible), RG-02 (déjà réservé) ou RG-03 (quota atteint)")
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.creerReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/reservations — 200, filtrable par statut et/ou adherentId.
     * ADHERENT : ses réservations seulement (RS-05, query adherentId ignoré).
     * BIBLIOTHECAIRE : toutes les réservations.
     */
    @Operation(summary = "Lister les réservations",
            description = "Filtrable par statut et/ou adherentId. "
                    + "ADHERENT : reçoit uniquement ses propres réservations, quel que soit le "
                    + "adherentId passé en query (RS-05). BIBLIOTHECAIRE : voit tout.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des réservations",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token absent/invalide")
    })
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listerReservations(
            @RequestParam(required = false) ReservationStatus statut,
            @RequestParam(required = false) Integer adherentId) {
        return ResponseEntity.ok(reservationService.listerReservations(statut, adherentId));
    }

    /**
     * GET /api/reservations/{id} — 200 si autorisé, 403 si la réservation
     * appartient à un autre adhérent (RS-03), 404 si inexistante.
     */
    @Operation(summary = "Consulter une réservation",
            description = "ADHERENT : uniquement ses propres réservations, sinon 403 (RS-03). "
                    + "BIBLIOTHECAIRE : toutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation trouvée",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token absent/invalide"),
            @ApiResponse(responseCode = "403", description = "Réservation appartenant à un autre adhérent (RS-03)"),
            @ApiResponse(responseCode = "404", description = "Réservation inexistante")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> consulterReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.consulterReservation(id));
    }

    /**
     * PATCH /api/reservations/{id}/annuler — 200 si autorisé, 403 si la
     * réservation appartient à un autre adhérent (RS-03), 409 si statut figé.
     */
    @Operation(summary = "Annuler une réservation",
            description = "Possible uniquement si le statut est EN_ATTENTE ou DISPONIBLE (RG-05) ; "
                    + "une réservation ANNULEE/EXPIREE/HONOREE ne change plus d'état (RG-06). "
                    + "ADHERENT : uniquement ses propres réservations (RS-03).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token absent/invalide"),
            @ApiResponse(responseCode = "403", description = "Réservation appartenant à un autre adhérent (RS-03)"),
            @ApiResponse(responseCode = "404", description = "Réservation inexistante"),
            @ApiResponse(responseCode = "409", description = "RG-05/RG-06 : statut ne permettant pas l'annulation")
    })
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.annulerReservation(id));
    }

    /**
     * DELETE /api/reservations/{id} — RS-02 : réservé au BIBLIOTHECAIRE.
     * Un ADHERENT reçoit 403 sans même passer par le service.
     */
    @Operation(summary = "Supprimer une réservation",
            description = "RS-02 : réservé au BIBLIOTHECAIRE. Un ADHERENT reçoit 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Réservation supprimée"),
            @ApiResponse(responseCode = "401", description = "Token absent/invalide"),
            @ApiResponse(responseCode = "403", description = "Rôle ADHERENT (RS-02)"),
            @ApiResponse(responseCode = "404", description = "Réservation inexistante")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}
