import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Books } from '../_model/books';
import { Users } from '../_model/users';
import { BooksService } from '../_service/books.service';
import { UsersService } from '../_service/users.service';
import { ReservationService } from '../_service/reservation.service';

/**
 * Formulaire de création d'une réservation.
 *
 *  - deux listes déroulantes alimentées par l'API (jamais de saisie d'ID) ;
 *  - bouton désactivé tant que les deux champs ne sont pas renseignés ;
 *  - les refus métier (409 RG-01/RG-02/RG-03, 400, 404) affichent le
 *    message réel du serveur, jamais un alert() ni un message générique.
 */
@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent implements OnInit {

  @Output()
  creee = new EventEmitter<void>();

  livres: Books[] = [];
  adherents: Users[] = [];

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
    this.chargerReferentiels();
  }

  private chargerReferentiels(): void {
    this.booksService.getBooksList().subscribe(
      (livres: Books[]) => { this.livres = livres; },
      (error: HttpErrorResponse) => {
        this.messageErreur = this.extraireMessage(error,
          "Impossible de charger la liste des livres.");
      }
    );
    this.usersService.getUsersList().subscribe(
      (users: Users[]) => { this.adherents = users; },
      (error: HttpErrorResponse) => {
        this.messageErreur = this.extraireMessage(error,
          "Impossible de charger la liste des adhérents.");
      }
    );
  }

  get formulaireIncomplet(): boolean {
    return this.livreId == null || this.adherentId == null;
  }

  onSoumettre(): void {
    if (this.formulaireIncomplet || this.enCours) {
      return;
    }
    this.enCours = true;
    this.messageSucces = '';
    this.messageErreur = '';

    this.reservationService.creerReservation({
      livreId: this.livreId!,
      adherentId: this.adherentId!
    }).subscribe(
      () => {
        this.enCours = false;
        this.messageSucces = 'Réservation créée avec succès.';
        this.livreId = null;
        this.adherentId = null;
        // La liste se rafraîchit via le conteneur, sans rechargement de page.
        this.creee.emit();
      },
      (error: HttpErrorResponse) => {
        this.enCours = false;
        // 409 (RG-01/RG-02/RG-03), 400, 404 : message réel du serveur.
        this.messageErreur = this.extraireMessage(error,
          "La création de la réservation a échoué.");
      }
    );
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
