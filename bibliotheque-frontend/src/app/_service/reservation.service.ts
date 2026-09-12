import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Reservation, ReservationRequest } from '../_model/reservation';

/**
 * Séance 3 : point d'appel HTTP unique du module Réservation
 * (le style suit books.service.ts ; le token JWT est ajouté
 * automatiquement par auth.interceptor.ts).
 */
@Injectable({
  providedIn: 'root'
})
export class ReservationService {

  private baseURL = "http://localhost:8080/api/reservations";

  constructor(private httpClient: HttpClient) { }

  /** POST /api/reservations -> 201, ou 400/404/409 avec {message} */
  createReservation(request: ReservationRequest): Observable<Reservation> {
    return this.httpClient.post<Reservation>(`${this.baseURL}`, request);
  }

  /** GET /api/reservations?statut=&adherentId= -> 200 */
  getReservations(statut?: string, adherentId?: number): Observable<Reservation[]> {
    const params: any = {};
    if (statut) {
      params.statut = statut;
    }
    if (adherentId != null) {
      params.adherentId = adherentId;
    }
    return this.httpClient.get<Reservation[]>(`${this.baseURL}`, { params });
  }

  /** GET /api/reservations/{id} -> 200, ou 403/404 avec {message} */
  getReservationById(reservationId: number): Observable<Reservation> {
    return this.httpClient.get<Reservation>(`${this.baseURL}/${reservationId}`);
  }

  /** PATCH /api/reservations/{id}/annuler -> 200, ou 403/409 avec {message} */
  annulerReservation(reservationId: number): Observable<Reservation> {
    return this.httpClient.patch<Reservation>(`${this.baseURL}/${reservationId}/annuler`, {});
  }

  /** DELETE /api/reservations/{id} -> 204 (BIBLIOTHECAIRE uniquement) */
  deleteReservation(reservationId: number): Observable<Object> {
    return this.httpClient.delete(`${this.baseURL}/${reservationId}`);
  }
}
