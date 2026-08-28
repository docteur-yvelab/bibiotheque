import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Books } from '../_model/books';
import { Reservation } from '../_model/reservation';
import { Users } from '../_model/users';

@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent {

  @Input() books: Books[] = [];
  @Input() users: Users[] = [];
  @Input() reservations: Reservation[] = [];
  @Input() errorMessage: string | null = null;
  @Output() created = new EventEmitter<Reservation>();

  selectedBookId: number | null = null;
  selectedUserId: number | null = null;

  private static readonly MAX_ACTIVE = 3;

  /** RG-01: Seuls les livres indisponibles (noOfCopies === 0) sont réservables */
  get unavailableBooks(): Books[] {
    return this.books.filter(b => b.noOfCopies === 0);
  }

  get activeReservationCount(): number {
    if (!this.selectedUserId) return 0;
    return this.reservations.filter(
      r => r.adherentId === this.selectedUserId && (r.statut === 'EN_ATTENTE' || r.statut === 'DISPONIBLE')
    ).length;
  }

  get isQuotaReached(): boolean {
    return this.activeReservationCount >= ReservationFormComponent.MAX_ACTIVE;
  }

  /** RG-02: Un adhérent ne peut avoir qu'une seule réservation active sur un même livre */
  get isDuplicateReservation(): boolean {
    if (!this.selectedUserId || !this.selectedBookId) return false;
    return this.reservations.some(
      r => r.adherentId === this.selectedUserId
        && r.livreId === this.selectedBookId
        && (r.statut === 'EN_ATTENTE' || r.statut === 'DISPONIBLE')
    );
  }

  get isFormValid(): boolean {
    return this.selectedBookId !== null
      && this.selectedUserId !== null
      && !this.isQuotaReached
      && !this.isDuplicateReservation;
  }

  onSubmit(): void {
    if (!this.isFormValid) {
      return;
    }
    const reservation = new Reservation();
    reservation.livreId = this.selectedBookId!;
    reservation.adherentId = this.selectedUserId!;
    this.created.emit(reservation);
    this.selectedBookId = null;
    this.selectedUserId = null;
  }
}
