import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ReservationResponse } from '../_model/reservation';
import { ReservationService } from '../_service/reservation.service';

/**
 * Liste des réservations avec ses 4 états (Séance 3, cœur du barème) :
 *  - chargement : indicateur visible pendant l'appel ;
 *  - données    : tableau rempli, filtre par statut ;
 *  - vide       : message explicite « Aucune réservation » ;
 *  - erreur     : message compréhensible + bouton Réessayer.
 */
@Component({
  selector: 'app-reservation-list',
  templateUrl: './reservation-list.component.html',
  styleUrls: ['./reservation-list.component.css']
})
export class ReservationListComponent implements OnInit {

  @Input()
  reservations: ReservationResponse[] = [];

  /** État courant : 'loading' | 'loaded' | 'empty' | 'error' */
  @Input()
  etat: string = 'loading';

  @Output()
  reessayer = new EventEmitter<void>();

  @Output()
  annuler = new EventEmitter<ReservationResponse>();

  /** Mémorise la réservation en cours d'annulation (spinner sur sa ligne). */
  annulationEnCoursId: number | null = null;

  /** Message d'un refus 409 renvoyé par le serveur lors d'une annulation. */
  messageAnnulation = '';

  constructor(private reservationService: ReservationService) { }

  ngOnInit(): void {
  }

  peutEtreAnnulee(reservation: ReservationResponse): boolean {
    return reservation.statut === 'EN_ATTENTE' || reservation.statut === 'DISPONIBLE';
  }

  onAnnuler(reservation: ReservationResponse): void {
    if (!window.confirm(
      `Confirmez-vous l'annulation de la réservation n°${reservation.reservationId} pour le livre "${reservation.bookName}" ?`
    )) {
      return;
    }
    this.messageAnnulation = '';
    this.annulationEnCoursId = reservation.reservationId;
    this.reservationService.annulerReservation(reservation.reservationId).subscribe(
      () => {
        this.annulationEnCoursId = null;
        this.annuler.emit(reservation);
      },
      (error: HttpErrorResponse) => {
        this.annulationEnCoursId = null;
        // Le serveur renvoie {message: "..."} via GlobalExceptionHandler :
        // on affiche son libellé réel, jamais un message générique.
        this.messageAnnulation = this.extraireMessage(error,
          "L'annulation a échoué. Vérifiez le statut de la réservation puis réessayez.");
      }
    );
  }

  /** Libellé lisible d'un statut pour l'interface en français. */
  libelleStatut(statut: string): string {
    const libelles: any = {
      'EN_ATTENTE': 'En attente',
      'DISPONIBLE': 'Disponible',
      'ANNULEE': 'Annulée',
      'EXPIREE': 'Expirée',
      'HONOREE': 'Honorée'
    };
    return libelles[statut] || statut;
  }

  private extraireMessage(error: HttpErrorResponse, defaut: string): string {
    if (error.error && typeof error.error.message === 'string' && error.error.message) {
      return error.error.message;
    }
    if (error.status === 0) {
      return 'Impossible de joindre le serveur. Vérifiez que le backend est démarré.';
    }
    return defaut;
  }
}
