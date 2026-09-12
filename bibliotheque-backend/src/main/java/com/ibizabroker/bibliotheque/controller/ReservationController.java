package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.ReservationService;
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
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> consulterReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.consulterReservation(id));
    }

    /**
     * PATCH /api/reservations/{id}/annuler — 200 si autorisé, 403 si la
     * réservation appartient à un autre adhérent (RS-03), 409 si statut figé.
     */
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.annulerReservation(id));
    }

    /**
     * DELETE /api/reservations/{id} — RS-02 : réservé au BIBLIOTHECAIRE.
     * Un ADHERENT reçoit 403 sans même passer par le service.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}
