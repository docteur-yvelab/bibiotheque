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
  @Input() errorMessage: string | null = null;
  @Output() created = new EventEmitter<Reservation>();

  selectedBookId: number | null = null;
  selectedUserId: number | null = null;

  get isFormValid(): boolean {
    return this.selectedBookId !== null && this.selectedUserId !== null;
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
