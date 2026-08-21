package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Réservations", description = "Gestion des réservations de livres")
@CrossOrigin("http://localhost:4200/")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Operation(summary = "Créer une réservation", description = "Crée une nouvelle réservation pour un livre indisponible. Le client envoie livreId et adherentId uniquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Champ(s) obligatoire(s) manquant(s)"),
            @ApiResponse(responseCode = "404", description = "Livre ou adherent non trouvé"),
            @ApiResponse(responseCode = "409", description = "Règle de gestion violée (RG-01, RG-02 ou RG-03)")
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.creerReservation(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Lister les réservations", description = "Liste toutes les réservations. Filtrable par statut et/ou adherentId via query params.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste retournée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listerReservations(
            @Parameter(description = "Filtrer par statut") @RequestParam(required = false) ReservationStatus statut,
            @Parameter(description = "Filtrer par identifiant adhérent") @RequestParam(required = false) Integer adherentId) {
        List<ReservationResponse> reservations = reservationService.listerReservations(statut, adherentId);
        return ResponseEntity.ok(reservations);
    }

    @Operation(summary = "Consulter une réservation", description = "Retourne les détails d'une réservation par son identifiant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation trouvée",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> consulterReservation(@PathVariable Integer id) {
        ReservationResponse response = reservationService.consulterReservation(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Annuler une réservation", description = "Annule une réservation. Seules les réservations EN_ATTENTE ou DISPONIBLE peuvent être annulées.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée avec succès",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée"),
            @ApiResponse(responseCode = "409", description = "Statut incompatible (RG-05/RG-06)")
    })
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        ReservationResponse response = reservationService.annulerReservation(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Supprimer une réservation", description = "Supprime définitivement une réservation.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Réservation supprimée"),
            @ApiResponse(responseCode = "404", description = "Réservation non trouvée")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}
