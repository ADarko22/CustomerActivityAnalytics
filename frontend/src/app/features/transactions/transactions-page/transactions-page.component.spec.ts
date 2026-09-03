import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { TransactionsPageComponent } from './transactions-page.component';

describe('TransactionsPageComponent', () => {
  let fixture: ComponentFixture<TransactionsPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TransactionsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        provideCharts(withDefaultRegisterables()),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ customerId: 'customer-1' }) } },
        },
      ],
    });
    fixture = TestBed.createComponent(TransactionsPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('reads the customerId from the route and passes it to the table', () => {
    expect(fixture.componentInstance.customerId()).toBe('customer-1');
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers/customer-1/transactions')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });
});
