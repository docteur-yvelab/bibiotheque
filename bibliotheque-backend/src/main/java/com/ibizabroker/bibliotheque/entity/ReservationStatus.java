package com.ibizabroker.bibliotheque.entity;

/**
 * Cycle de vie d'une réservation.
 *
 * EN_ATTENTE : le livre est indisponible, l'adhérent attend une retour.
 * DISPONIBLE : le livre est revenu, la réservation attend d'être honorée.
 * ANNULEE    : annulée par l'adhérent (transition finale).
 * EXPIREE    : dateExpiration dépassée sans retour (transition finale).
 * HONOREE    : l'emprunt a été réalisé (transition finale).
 */
public enum ReservationStatus {
    EN_ATTENTE,
    DISPONIBLE,
    ANNULEE,
    EXPIREE,
    HONOREE
}
