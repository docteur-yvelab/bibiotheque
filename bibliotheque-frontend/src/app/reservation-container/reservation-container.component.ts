import { Component, OnInit } from '@angular/core';
import { Books } from '../_model/books';
import { Reservation } from '../_model/reservation';
import { Users } from '../_model/users';
import { BooksService } from '../_service/books.service';
import { ReservationService } from '../_service/reservation.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-reservation-container',
  templateUrl: './reservation-container.component.html',
  styleUrls: ['./reservation-container.component.css']
})
export class ReservationContainerComponent implements OnInit {

  reservations: Reservation[] = [];
  books: Books[] = [];
  users: Users[] = [];
  loading = true;
  error: string | null = null;
  formError: string | null = null;
  successMessage: string | null = null;

  constructor(
    private reservationService: ReservationService,
    private booksService: BooksService,
    private usersService: UsersService
  ) { }

  ngOnInit(): void {
    this.loadReservations();
    this.loadBooks();
    this.loadUsers();
  }

  loadReservations(): void {
    this.loading = true;
    this.error = null;
    this.reservationService.getAll().subscribe(
      (data) => {
        this.reservations = data;
        this.loading = false;
      },
      (err) => {
        this.loading = false;
        if (err.status === 0) {
          this.error = 'Cannot reach the server. The backend may be stopped or unreachable. Please verify that the server is running and try again.';
        } else if (err.status === 401) {
          this.error = 'Your session has expired. Please log in again.';
        } else if (err.status === 403) {
          this.error = 'You do not have permission to view reservations. Contact your administrator.';
        } else if (err.status === 500) {
          this.error = 'An internal server error occurred. Please try again later or contact support.';
        } else {
          this.error = 'An unexpected error occurred while loading reservations (HTTP ' + err.status + '). Please try again.';
        }
      }
    );
  }

  loadBooks(): void {
    this.booksService.getBooksList().subscribe(
      (data) => this.books = data,
      () => this.books = []
    );
  }

  loadUsers(): void {
    this.usersService.getUsersList().subscribe(
      (data) => this.users = data,
      () => this.users = []
    );
  }

  onReservationCreated(reservation: Reservation): void {
    this.formError = null;
    this.successMessage = null;
    this.reservationService.create(reservation).subscribe(
      () => {
        this.successMessage = 'Reservation created successfully.';
        this.loadReservations();
        setTimeout(() => this.successMessage = null, 4000);
      },
      (err) => {
        if (err.status === 409) {
          this.formError = err.error?.message || 'Cannot create reservation. This book may already be reserved, the quota of 3 active reservations may have been reached, or the book is currently available for direct borrowing.';
        } else if (err.status === 400) {
          this.formError = err.error?.message || 'Missing or invalid fields. Please check that you have selected both a book and a member.';
        } else if (err.status === 404) {
          this.formError = err.error?.message || 'The selected book or member was not found on the server. Please refresh the page and try again.';
        } else if (err.status === 0) {
          this.formError = 'Cannot reach the server. The backend may be stopped. Please verify the server is running and try again.';
        } else {
          this.formError = 'An unexpected error occurred (HTTP ' + err.status + '). Please try again.';
        }
      }
    );
  }

  onReservationCancelled(reservationId: number): void {
    this.successMessage = null;
    this.reservationService.annuler(reservationId).subscribe(
      () => {
        this.successMessage = 'Reservation cancelled successfully.';
        this.loadReservations();
        setTimeout(() => this.successMessage = null, 4000);
      },
      (err) => {
        if (err.status === 409) {
          this.error = err.error?.message || 'Cannot cancel this reservation. It may have already been processed, expired, or is no longer in a cancellable state (EN_ATTENTE or DISPONIBLE).';
        } else if (err.status === 404) {
          this.error = 'This reservation was not found on the server. It may have already been deleted.';
        } else if (err.status === 0) {
          this.error = 'Cannot reach the server. The backend may be stopped. Please verify the server is running and try again.';
        } else {
          this.error = 'An unexpected error occurred while cancelling the reservation (HTTP ' + err.status + '). Please try again.';
        }
        setTimeout(() => this.error = null, 6000);
      }
    );
  }

}
