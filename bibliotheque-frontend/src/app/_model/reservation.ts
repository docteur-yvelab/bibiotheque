export class ReservationResponse {
    reservationId: number;
    livreId: number;
    adherentId: number;
    bookName: string;
    adherentName: string;
    dateReservation: string;
    dateExpiration: string;
    statut: string;
}

export class ReservationRequest {
    livreId: number;
    adherentId: number;
}

/** Statuts exposés par le backend (enum ReservationStatus). */
export const STATUTS_RESERVATION: string[] = [
    'EN_ATTENTE',
    'DISPONIBLE',
    'ANNULEE',
    'EXPIREE',
    'HONOREE'
];
