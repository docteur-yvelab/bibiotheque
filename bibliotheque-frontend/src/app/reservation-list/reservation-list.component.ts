import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Reservation } from '../_model/reservation';
import { ReservationService } from '../_service/reservation.service';

/**
 * Liste des réservations (Séance 3) — les 4 états obligatoires :
 *  - chargement : indicateur visible, jamais un écran figé
 *  - données : tableau rempli
 *  - liste vide : message explicite « Aucune réservation »
 *  - erreur : message compréhensible + bouton Réessayer
 *
 * Le filtre par statut et l'action d'annulation (avec confirmation,
 * message serveur si 409) sont gérés ici.
 */
@Component({
  selector: 'app-reservation-list',
  templateUrl: './reservation-list.component.html',
  styleUrls: ['./reservation-list.component.css']
})
export class ReservationListComponent implements OnChanges {

  @Input() reservations: Reservation[] = [];
  @Input() enChargement = false;
  @Input() erreurChargement = '';
  @Input() filtreStatut = 'TOUS';

  @Output() filtreChange = new EventEmitter<string>();
  @Output() reessayer = new EventEmitter<void>();
  @Output() annulationDemandee = new EventEmitter<Reservation>();
  @Output() suppressionDemandee = new EventEmitter<Reservation>();

  statuts: string[] = ['TOUS', 'EN_ATTENTE', 'DISPONIBLE', 'HONOREE', 'ANNULEE', 'EXPIREE'];

  @Input() estBibliothecaire = false;

  /** Message d'erreur d'une action (annulation/suppression) affiché sous le filtre */
  messageAction = '';

  constructor(private reservationService: ReservationService) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reservations']) {
      this.messageAction = '';
    }
  }

  get reservationsFiltrees(): Reservation[] {
    if (this.filtreStatut === 'TOUS') {
      return this.reservations;
    }
    return this.reservations.filter(r => r.statut === this.filtreStatut);
  }

  /** Annulation possible seulement si EN_ATTENTE ou DISPONIBLE (RG-05) */
  estAnnulable(reservation: Reservation): boolean {
    return reservation.statut === 'EN_ATTENTE' || reservation.statut === 'DISPONIBLE';
  }

  onFiltreChange(valeur: string): void {
    this.filtreChange.emit(valeur);
  }

  onReessayer(): void {
    this.reessayer.emit();
  }

  onAnnuler(reservation: Reservation): void {
    const confirmee = window.confirm(
      'Annuler la réservation n° ' + reservation.reservationId +
      ' pour « ' + (reservation.bookName || 'livre #' + reservation.livreId) + ' » ?'
    );
    if (confirmee) {
      this.annulationDemandee.emit(reservation);
    }
  }

  onSupprimer(reservation: Reservation): void {
    const confirmee = window.confirm(
      'Supprimer définitivement la réservation n° ' + reservation.reservationId + ' ?'
    );
    if (confirmee) {
      this.suppressionDemandee.emit(reservation);
    }
  }

  /** Appelé par le conteneur pour afficher une erreur d'action (409 etc.) */
  afficherErreur(message: string): void {
    this.messageAction = message;
  }

  /** Libellés français lisibles pour les statuts (et le filtre « TOUS ») */
  libelleStatut(statut: string): string {
    const libelles: any = {
      'TOUS': 'Tous',
      'EN_ATTENTE': 'En attente',
      'DISPONIBLE': 'Disponible',
      'HONOREE': 'Honorée',
      'ANNULEE': 'Annulée',
      'EXPIREE': 'Expirée'
    };
    return libelles[statut] || statut;
  }

  formaterDate(iso: string): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }
}
