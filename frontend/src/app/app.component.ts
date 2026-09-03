import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CustomerSearchComponent } from './features/customer-search/customer-search.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CustomerSearchComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly title = 'Customer Activity Analytics';
}
