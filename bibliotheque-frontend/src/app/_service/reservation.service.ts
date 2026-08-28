import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Reservation } from '../_model/reservation';

@Injectable({
  providedIn: 'root'
})
export class ReservationService {

  private baseURL = "http://localhost:8080/api/reservations";

  constructor(private httpClient: HttpClient) { }

  getAll(): Observable<Reservation[]> {
    return this.httpClient.get<Reservation[]>(`${this.baseURL}`);
  }

  create(reservation: Reservation): Observable<Object> {
    return this.httpClient.post(`${this.baseURL}`, reservation);
  }

  annuler(reservationId: number): Observable<Object> {
    return this.httpClient.patch(`${this.baseURL}/${reservationId}/annuler`, {});
  }

  honorer(reservationId: number): Observable<Object> {
    return this.httpClient.patch(`${this.baseURL}/${reservationId}/honorer`, {});
  }
}
