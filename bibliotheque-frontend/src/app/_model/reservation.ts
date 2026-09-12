/**
 * Modèles du module Réservation (Séance 3).
 * Miroir des DTOs backend ReservationRequest / ReservationResponse.
 */
export interface Reservation {
  reservationId: number;
  livreId: number;
  adherentId: number;
  dateReservation: string;
  dateExpiration: string;
  statut: 'EN_ATTENTE' | 'DISPONIBLE' | 'HONOREE' | 'ANNULEE' | 'EXPIREE';
  // Champs enrichis par le backend (jointure côté service)
  bookName?: string;
  adherentName?: string;
}

export interface ReservationRequest {
  livreId: number;
  adherentId?: number;
}
