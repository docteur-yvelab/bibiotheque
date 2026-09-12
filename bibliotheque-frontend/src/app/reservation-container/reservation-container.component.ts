import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ReservationResponse } from '../_model/reservation';
import { ReservationService } from '../_service/reservation.service';

/**
 * Conteneur du module Réservation : possède l'état global de l'écran
 * (liste, filtre, 4 états d'affichage) et orchestre la liste et le
 * formulaire. Les appels HTTP passent exclusivement par ReservationService.
 */
@Component({
  selector: 'app-reservation-container',
  templateUrl: './reservation-container.component.html',
  styleUrls: ['./reservation-container.component.css']
})
export class ReservationContainerComponent implements OnInit {

  reservations: ReservationResponse[] = [];

  /** État courant : 'loading' | 'loaded' | 'empty' | 'error' */
  etat: string = 'loading';

  filtreStatut = '';

  constructor(private reservationService: ReservationService) { }

  ngOnInit(): void {
    this.chargerReservations();
  }

  chargerReservations(): void {
    this.etat = 'loading';
    this.reservationService.getReservations(this.filtreStatut || undefined).subscribe(
      (reservations: ReservationResponse[]) => {
        this.reservations = reservations;
        this.etat = reservations.length === 0 ? 'empty' : 'loaded';
      },
      (error: HttpErrorResponse) => {
        this.etat = 'error';
      }
    );
  }

  onFiltreStatut(statut: string): void {
    this.filtreStatut = statut;
    this.chargerReservations();
  }

  /** Après une création réussie : rafraîchit la liste sans recharger la page. */
  onReservationCreee(): void {
    this.chargerReservations();
  }

  /** Mise à jour en place après annulation : pas de nouvel appel réseau. */
  onReservationAnnulee(annulee: ReservationResponse): void {
    const index = this.reservations.findIndex(r => r.reservationId === annulee.reservationId);
    if (index !== -1) {
      this.reservations[index] = { ...annulee, statut: 'ANNULEE' };
    }
    this.etat = this.reservations.length === 0 ? 'empty' : 'loaded';
  }
}
