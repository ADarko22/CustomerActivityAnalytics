import { Component } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: ` <p class="empty-state">Search for a customer above to view their activity.</p> `,
  styles: [
    `
      .empty-state {
        padding: 2rem;
        text-align: center;
        color: rgba(0, 0, 0, 0.6);
      }
    `,
  ],
})
export class EmptyStateComponent {}
