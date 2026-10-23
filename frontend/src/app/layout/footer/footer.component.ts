import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BrandLogoComponent } from '../../shared/components/brand-logo/brand-logo.component';

/** The app-wide footer: brand blurb, link columns and the legal line. */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink, BrandLogoComponent],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FooterComponent {

  readonly year = new Date().getFullYear();

  /** Link columns — real routes where pages exist, '#' placeholders elsewhere. */
  readonly columns = [
    {
      title: 'Shop',
      links: [
        { label: 'All products', route: '/products' },
        { label: 'Electronics', route: '/products' },
        { label: 'Fashion', route: '/products' },
        { label: 'Home & Kitchen', route: '/products' },
      ],
    },
    {
      title: 'Account',
      links: [
        { label: 'My account', route: '/account' },
        { label: 'My orders', route: '/orders' },
        { label: 'My cart', route: '/cart' },
        { label: 'Sign in', route: '/login' },
      ],
    },
    {
      title: 'Company',
      links: [
        { label: 'About MetaShop', route: '#' },
        { label: 'Careers', route: '#' },
        { label: 'Privacy policy', route: '#' },
        { label: 'Terms of service', route: '#' },
      ],
    },
  ];
}
