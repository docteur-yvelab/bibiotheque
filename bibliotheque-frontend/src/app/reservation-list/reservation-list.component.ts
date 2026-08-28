import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { Reservation } from '../_model/reservation';

@Component({
  selector: 'app-reservation-list',
  templateUrl: './reservation-list.component.html',
  styleUrls: ['./reservation-list.component.css']
})
export class ReservationListComponent implements OnInit {

  @Input() reservations: Reservation[] = [];
  @Output() cancelled = new EventEmitter<number>();

  filterStatus = 'ALL';
  filteredReservations: Reservation[] = [];

  ngOnInit(): void {
    this.applyFilter();
  }

  ngOnChanges(): void {
    this.applyFilter();
  }

  applyFilter(): void {
    if (!this.filterStatus || this.filterStatus === 'ALL') {
      this.filteredReservations = [...this.reservations];
    } else {
      this.filteredReservations = this.reservations.filter(
        r => r.status === this.filterStatus
      );
    }
  }

  getBookName(r: Reservation): string {
    return r.book?.bookName || r.book?.name || ('Book #' + r.livreId);
  }

  getUserName(r: Reservation): string {
    return r.user?.name || r.user?.username || ('User #' + r.adherentId);
  }

  canCancel(status: string): boolean {
    return status === 'EN_ATTENTE' || status === 'DISPONIBLE';
  }

  confirmCancel(reservationId: number): void {
    if (confirm('Are you sure you want to cancel this reservation? This action cannot be undone.')) {
      this.cancelled.emit(reservationId);
    }
  }
}
