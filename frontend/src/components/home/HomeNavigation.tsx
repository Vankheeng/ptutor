import { useState } from 'react';
import './HomeNavigation.css';
import { HOME_NAVIGATION_MENUS, type HomeNavigationItem } from './homeContent';

interface HomeNavigationProps {
  isOpen: boolean;
}

export function HomeNavigation({ isOpen }: HomeNavigationProps) {
  return (
    <nav className={isOpen ? 'nav-links is-open' : 'nav-links'} aria-label="Điều hướng chính">
      <a className="active" href="#top">
        Trang chủ
      </a>
      {HOME_NAVIGATION_MENUS.map((menu) => (
        <NavigationDropdown key={menu.label} {...menu} />
      ))}
    </nav>
  );
}

function NavigationDropdown({ label, items }: { label: string; items: readonly HomeNavigationItem[] }) {
  const [open, setOpen] = useState(false);

  return (
    <div className={open ? 'nav-dropdown is-open' : 'nav-dropdown'}>
      <button
        type="button"
        className="nav-dropdown-trigger"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        <span>{label}</span>
        <span className="nav-chevron" aria-hidden="true" />
      </button>
      <div className="nav-dropdown-menu">
        {items.map((item) => (
          <a href={item.href} key={item.label} onClick={() => setOpen(false)}>
            {item.label}
          </a>
        ))}
      </div>
    </div>
  );
}
