import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Reservation } from '../_model/reservation';
import { UserAuthService } from '../_service/user-auth.service';
import { UsersService } from '../_service/users.service';
import { ReservationFormComponent } from '../reservation-form/reservation-form.component';
import { ReservationListComponent } from '../reservation-list/reservation-list.component';
import { ReservationService } from '../_service/reservation.service';

/**
 * Composant conteneur de l'écran Réservations (Séance 3).
 *
 * Gère l'état global de l'écran (chargement / données / vide / erreur),
 * les appels au ReservationService et la coordination
 * formulaire -> liste. Aucun HttpClient direct ici : tout passe
 * par les services (_service/reservation.service.ts).
 */
@Component({
  selector: 'app-reservation-container',
  templateUrl: './reservation-container.component.html',
  styleUrls: ['./reservation-container.component.css']
})
export class ReservationContainerComponent implements OnInit {

  @ViewChild(ReservationListComponent) listeEnfant!: ReservationListComponent;

  reservations: Reservation[] = [];
  enChargement = true;
  erreurChargement = '';
  filtreStatut = 'TOUS';

  estBibliothecaire = false;

  constructor(
    private reservationService: ReservationService,
    private userAuthService: UserAuthService,
    private usersService: UsersService
  ) { }

  ngOnInit(): void {
    // Les rôles stockés à la connexion portent le roleName EXACT (Admin,
    // ADHERENT, BIBLIOTHECAIRE) — on teste les deux conventions existantes.
    const bibliothecaire = this.usersService.roleMatch(['BIBLIOTHECAIRE']);
    const admin = this.usersService.roleMatch(['Admin']);
    this.estBibliothecaire = bibliothecaire || admin;
    this.chargerReservations();
  }

  chargerReservations(): void {
    this.enChargement = true;
    this.erreurChargement = '';

    this.reservationService.getReservations().subscribe(
      (data) => {
        this.reservations = data;
        this.enChargement = false;
      },
      (error: HttpErrorResponse) => {
        this.enChargement = false;
        if (error.status === 0) {
          this.erreurChargement = 'Le serveur est injoignable. Vérifiez qu\'il est démarré, puis réessayez.';
        } else if (error.error && error.error.message) {
          this.erreurChargement = error.error.message;
        } else {
          this.erreurChargement = 'Erreur inattendue (code ' + error.status + ').';
        }
      }
    );
  }

  onFiltreChange(statut: string): void {
    this.filtreStatut = statut;
    // Filtre 100% côté client : la liste est déjà chargée, inutile de
    // refaire un appel réseau. (RS-05 garantit côté serveur qu'un
    // ADHERENT ne voit de toute façon que les siennes.)
  }

  onReessayer(): void {
    this.chargerReservations();
  }

  onReservationCreee(): void {
    // Rafraîchissement de la liste sans recharger la page
    this.chargerReservations();
  }

  onAnnulationDemandee(reservation: Reservation): void {
    this.reservationService.annulerReservation(reservation.reservationId).subscribe(
      (miseAJour) => {
        // Mise à jour du statut dans la liste sans recharger la page
        reservation.statut = miseAJour.statut;
      },
      (error: HttpErrorResponse) => {
        this.listeEnfant.afficherErreur(this.extraireMessage(error));
      }
    );
  }

  onSuppressionDemandee(reservation: Reservation): void {
    this.reservationService.deleteReservation(reservation.reservationId).subscribe(
      () => {
        this.reservations = this.reservations.filter(r => r.reservationId !== reservation.reservationId);
      },
      (error: HttpErrorResponse) => {
        this.listeEnfant.afficherErreur(this.extraireMessage(error));
      }
    );
  }

  /** Message RÉEL du serveur (champ "message" de GlobalExceptionHandler) */
  private extraireMessage(error: HttpErrorResponse): string {
    if (error.error && typeof error.error.message === 'string' && error.error.message) {
      return error.error.message;
    }
    switch (error.status) {
      case 0: return 'Serveur injoignable.';
      case 403: return 'Vous n\'avez pas les droits pour cette action.';
      case 409: return 'Cette réservation ne peut plus être modifiée.';
      case 404: return 'Réservation introuvable.';
      default: return 'Erreur inattendue (code ' + error.status + ').';
    }
  }
}
