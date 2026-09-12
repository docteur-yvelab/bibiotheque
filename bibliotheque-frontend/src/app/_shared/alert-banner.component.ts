import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Bandeau d'alerte partagé (succès / erreur / avertissement).
 * Utilisé par le formulaire et la liste des réservations pour afficher
 * les messages réels renvoyés par le backend — jamais d'alert().
 */
@Component({
  selector: 'app-alert-banner',
  template: `
    <div class="alert alert-dismissible fade show mb-3" [ngClass]="'alert-' + type" role="alert">
      {{ message }}<ng-content></ng-content>
      <button *ngIf="dismissible" type="button" class="btn-close" aria-label="Fermer" (click)="closed.emit()"></button>
    </div>
  `
})
export class AlertBannerComponent {

  /** Couleur Bootstrap : success | danger | warning */
  @Input()
  type: string = 'info';

  @Input()
  message: string = '';

  @Input()
  dismissible: boolean = true;

  @Output()
  closed = new EventEmitter<void>();
}
