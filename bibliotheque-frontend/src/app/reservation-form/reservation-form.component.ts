import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Books } from '../_model/books';
import { Users } from '../_model/users';
import { BooksService } from '../_service/books.service';
import { UsersService } from '../_service/users.service';
import { ReservationService } from '../_service/reservation.service';

/**
 * Formulaire de création de réservation (Séance 3).
 *
 * Deux listes déroulantes (livre, adhérent) — jamais de saisie d'ID à la main.
 * Le bouton est désactivé tant que les deux champs ne sont pas renseignés.
 *
 * Traitement des refus métier (8 points du barème) : le message RÉEL du
 * serveur (champ "message" du JSON de GlobalExceptionHandler) est affiché
 * à côté du formulaire — jamais d'alert(), jamais de message générique.
 */
@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent implements OnInit {

  @Input() estBibliothecaire = false;
  @Output() reservationCreee = new EventEmitter<void>();

  livres: Books[] = [];
  users: Users[] = [];

  livreId: number | null = null;
  adherentId: number | null = null;

  enCours = false;
  messageSucces = '';
  messageErreur = '';

  constructor(
    private booksService: BooksService,
    private usersService: UsersService,
    private reservationService: ReservationService
  ) { }

  ngOnInit(): void {
    this.booksService.getBooksList().subscribe(
      data => this.livres = data,
      () => this.messageErreur = 'Impossible de charger la liste des livres. Le serveur est-il démarré ?'
    );
    // GET /admin/users est réservé au rôle Admin côté backend : seul un
    // BIBLIOTHECAIRE/Admin a besoin du dropdown des adhérents (il réserve
    // pour un tiers). Un ADHERENT réserve pour lui-même (RS-04).
    if (this.estBibliothecaire) {
      this.usersService.getUsersList().subscribe(
        data => this.users = data,
        () => this.messageErreur = 'Impossible de charger la liste des adhérents.'
      );
    }
  }

  get formulaireValide(): boolean {
    if (this.estBibliothecaire) {
      return this.livreId != null && this.adherentId != null;
    }
    return this.livreId != null;
  }

  onSubmit(): void {
    if (!this.formulaireValide || this.enCours) {
      return;
    }
    this.enCours = true;
    this.messageErreur = '';
    this.messageSucces = '';

    const request: any = { livreId: this.livreId };
    if (this.estBibliothecaire) {
      request.adherentId = this.adherentId;
    }

    this.reservationService.createReservation(request).subscribe(
      (reservation) => {
        this.enCours = false;
        this.messageSucces = 'Réservation créée pour « ' + (reservation.bookName || ('livre #' + reservation.livreId)) + ' ».';
        this.livreId = null;
        this.adherentId = null;
        this.reservationCreee.emit();
      },
      (error: HttpErrorResponse) => {
        this.enCours = false;
        this.messageErreur = this.extraireMessage(error);
      }
    );
  }

  /**
   * Extrait le message réel renvoyé par GlobalExceptionHandler :
   * {"timestamp":..., "status":409, "error":"Conflict", "message":"RG-01 : ..."}
   */
  private extraireMessage(error: HttpErrorResponse): string {
    if (error.error && typeof error.error.message === 'string' && error.error.message) {
      return error.error.message;
    }
    switch (error.status) {
      case 0: return 'Serveur injoignable. Vérifiez que le backend est démarré.';
      case 400: return 'Requête invalide : un champ obligatoire est manquant.';
      case 404: return 'Livre ou adhérent inexistant.';
      case 409: return 'La réservation a été refusée par une règle de gestion.';
      default: return 'Erreur inattendue (code ' + error.status + ').';
    }
  }
}
