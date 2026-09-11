package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.entity.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.CurrentUserService;
import com.ibizabroker.bibliotheque.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CurrentUserService currentUserService;

    // POST /api/reservations — Créer une réservation
    @PostMapping
    public ResponseEntity<ReservationResponse> creerReservation(@RequestBody ReservationRequest request) {
        Integer currentUserId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.creerReservation(request, currentUserId, isBiblio);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/reservations — Lister les réservations
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listerReservations(
            @RequestParam(required = false) ReservationStatus statut,
            @RequestParam(required = false) Integer adherentId) {

        Integer currentUserId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        List<ReservationResponse> list = reservationService.listerReservations(statut, adherentId, currentUserId, isBiblio);
        return ResponseEntity.ok(list);
    }

    // GET /api/reservations/{id} — Consulter une réservation
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> consulterReservation(@PathVariable Integer id) {
        Integer currentUserId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.consulterReservation(id, currentUserId, isBiblio);
        return ResponseEntity.ok(response);
    }

    // PATCH /api/reservations/{id}/annuler — Annuler une réservation
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<ReservationResponse> annulerReservation(@PathVariable Integer id) {
        Integer currentUserId = currentUserService.getAuthenticatedUserId();
        boolean isBiblio = currentUserService.isBibliothecaire();

        ReservationResponse response = reservationService.annulerReservation(id, currentUserId, isBiblio);
        return ResponseEntity.ok(response);
    }

    // DELETE /api/reservations/{id} — Supprimer une réservation (BIBLIOTHECAIRE uniquement)
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerReservation(@PathVariable Integer id) {
        reservationService.supprimerReservation(id);
        return ResponseEntity.noContent().build();
    }
}