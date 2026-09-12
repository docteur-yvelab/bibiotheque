import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ReservationRequest, ReservationResponse } from '../_model/reservation';

/**
 * Seul point d'appel HTTP du module Réservation.
 * Aucun composant n'utilise HttpClient directement pour ce module —
 * ils passent tous par ce service (même convention que books.service.ts).
 */
@Injectable({
  providedIn: 'root'
})
export class ReservationService {

  private baseURL = "http://localhost:8080/api/reservations";

  constructor(private httpClient: HttpClient) { }

  getReservations(statut?: string, adherentId?: number): Observable<ReservationResponse[]> {
    let params = new HttpParams();
    if (statut) {
      params = params.set('statut', statut);
    }
    if (adherentId != null) {
      params = params.set('adherentId', String(adherentId));
    }
    return this.httpClient.get<ReservationResponse[]>(`${this.baseURL}`, { params: params });
  }

  getReservationById(reservationId: number): Observable<ReservationResponse> {
    return this.httpClient.get<ReservationResponse>(`${this.baseURL}/${reservationId}`);
  }

  creerReservation(request: ReservationRequest): Observable<Object> {
    return this.httpClient.post(`${this.baseURL}`, request);
  }

  annulerReservation(reservationId: number): Observable<Object> {
    return this.httpClient.patch(`${this.baseURL}/${reservationId}/annuler`, null);
  }

  supprimerReservation(reservationId: number): Observable<Object> {
    return this.httpClient.delete(`${this.baseURL}/${reservationId}`);
  }
}
