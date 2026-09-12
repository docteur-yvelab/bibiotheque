package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Point d'entrée HTTP du module Réservation.
 *
 * Aucune logique métier ici : le contrôleur délègue tout au service.
 *
 * Sécurité (Séance 4) :
 *  RS-01 — tous les endpoints exigent un token valide (401 sinon) ;
 *  RS-02 — DELETE réservé au BIBLIOTHECAIRE via @PreAuthorize (403 sinon) ;
 *  RS-03 / RS-05 — vérifiés dans ReservationService (propriété + filtrage) ;
 *  RS-04 — pour un ADHERENT, l'adherentId du body est ignoré : l'identité
 *          vient du token. Le BIBLIOTHECAIRE peut créer pour un tiers.
 */
@Tag(name = "Réservations", description = "Gestion des réservations de livres (RG-01 à RG-06, RS-01 à RS-05)")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Operation(summary = "Créer une réservation",
            description = "Crée une réservation pour un adhérent sur un livre indisponible. "
                    + "ADHERENT : la réservation est créée pour lui-même, l'adherentId du corps est ignoré (RS-04). "
                    + "BIBLIOTHECAIRE : peut créer pour n'importe quel adhérent via adherentId. "
                    + "RG-01 : le livre doit être indisponible. RG-02 : une seule réservation active "
                    + "par adhérent et par livre. RG-03 : maximum 3 réservations actives. "
                    + "RG-04 : la date d'expiration (+7 jours) est calculée par le serveur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée"),
            @ApiResponse(responseCode = "400", description = "Champ obligatoire manquant (livreId, ou adherentId pour un BIBLIOTHECAIRE)"),
            @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré"),
            @ApiResponse(responseCode = "404", description = "Livre ou adhérent introuvable"),
            @ApiResponse(responseCode = "409", description = "Règle de gestion violée (RG-01, RG-02 ou RG-03)")
    })
    @PostMapping
    public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.creerReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Lister les réservations",
            description = "ADHERENT : retourne uniquement ses propres réservations, quels que soient les filtres (RS-05). "
                    + "BIBLIOTHECAIRE : voit tout et peut filtrer par statut et par adhérent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des réservations (éventuellement vide)"),
            @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré")
    })
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listerReservations(
            @Parameter(description = "Filtrer par statut") @RequestParam(required = false) ReservationStatus statut,
            @Parameter(description = "Filtrer par adhérent (BIBLIOTHECAIRE uniquement)") @RequestParam(required = false) Integer adherentId) {
        return ResponseEntity.ok(reservationService.listerReservations(statut, adherentId));
    }

    @Operation(summary = "Consulter une réservation",
            description = "ADHERENT : uniquement ses propres réservations, sinon 403 (RS-03). "
                    + "BIBLIOTHECAIRE : accès à toutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation trouvée"),
            @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré"),
            @ApiResponse(responseCode = "403", description = "Réservation appartenant à un autre adhérent (RS-03)"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> obtenirReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.obtenirReservation(id));
    }

    @Operation(summary = "Annuler une réservation",
            description = "ADHERENT : uniquement ses propres réservations, sinon 403 (RS-03). "
                    + "BIBLIOTHECAIRE : peut annuler n'importe laquelle. "
                    + "RG-05 : seules les réservations EN_ATTENTE ou DISPONIBLE peuvent être annulées. "
                    + "RG-06 : une réservation ANNULEE, EXPIREE ou HONOREE ne change plus d'état.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée"),
            @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré"),
            @ApiResponse(responseCode = "403", description = "Réservation appartenant à un autre adhérent (RS-03)"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable"),
            @ApiResponse(responseCode = "409", description = "Règle de gestion violée (RG-05 / RG-06)")
    })
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.annulerReservation(id));
    }

    @Operation(summary = "Supprimer une réservation",
            description = "Réservé au BIBLIOTHECAIRE (RS-02) : un ADHERENT reçoit 403, jamais 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Réservation supprimée"),
            @ApiResponse(responseCode = "401", description = "Token absent, invalide ou expiré"),
            @ApiResponse(responseCode = "403", description = "Réservé au BIBLIOTHECAIRE (RS-02)"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}
