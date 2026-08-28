import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { Reservation } from '../_model/reservation';
import { Books } from '../_model/books';
import { Users } from '../_model/users';

@Component({
  selector: 'app-reservation-list',
  templateUrl: './reservation-list.component.html',
  styleUrls: ['./reservation-list.component.css']
})
export class ReservationListComponent implements OnInit, OnChanges {

  @Input() reservations: Reservation[] = [];
  @Input() books: Books[] = [];
  @Input() users: Users[] = [];
  @Output() cancelled = new EventEmitter<number>();
  @Output() deleted = new EventEmitter<number>();

  filterStatus = 'ALL';
  filteredReservations: Reservation[] = [];

  private booksMap: Map<number, Books> = new Map();
  private usersMap: Map<number, Users> = new Map();

  ngOnInit(): void {
    this.buildMaps();
    this.applyFilter();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['books'] || changes['users']) {
      this.buildMaps();
    }
    if (changes['reservations']) {
      this.applyFilter();
    }
  }

  private buildMaps(): void {
    this.booksMap.clear();
    this.usersMap.clear();
    this.books.forEach(b => this.booksMap.set(b.bookId, b));
    this.users.forEach(u => this.usersMap.set(u.userId, u));
  }

  applyFilter(): void {
    if (!this.filterStatus || this.filterStatus === 'ALL') {
      this.filteredReservations = [...this.reservations];
    } else {
      this.filteredReservations = this.reservations.filter(
        r => r.statut === this.filterStatus
      );
    }
  }

  getBookName(r: Reservation): string {
    const book = this.booksMap.get(r.livreId);
    return book ? book.bookName : ('Book #' + r.livreId);
  }

  getUserName(r: Reservation): string {
    const user = this.usersMap.get(r.adherentId);
    return user ? user.name : ('User #' + r.adherentId);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return dateStr;
  }

  getStatusLabel(statut: string): string {
    if (!statut) return '—';
    const labels: Record<string, string> = {
      'EN_ATTENTE': 'En attente',
      'DISPONIBLE': 'Disponible',
      'ANNULEE': 'Annulée',
      'EXPIREE': 'Expirée',
      'HONOREE': 'Honorée'
    };
    return labels[statut] || statut;
  }

  canCancel(statut: string): boolean {
    return statut === 'EN_ATTENTE' || statut === 'DISPONIBLE';
  }

  confirmCancel(reservationId: number): void {
    if (confirm('Are you sure you want to cancel this reservation? This action cannot be undone.')) {
      this.cancelled.emit(reservationId);
    }
  }

  confirmDelete(reservationId: number): void {
    if (confirm('Are you sure you want to permanently delete this reservation? This action cannot be undone.')) {
      this.deleted.emit(reservationId);
    }
  }

  canDelete(statut: string): boolean {
    return statut === 'EN_ATTENTE' || statut === 'DISPONIBLE';
  }

}
