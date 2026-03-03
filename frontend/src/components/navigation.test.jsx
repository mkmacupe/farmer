import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { NAV_ITEMS, itemIcon, roleLabel, sectionLabel } from './navigation.jsx';

describe('navigation helpers', () => {
  it('contains nav items for all app roles', () => {
    expect(NAV_ITEMS.DIRECTOR).toHaveLength(4);
    expect(NAV_ITEMS.MANAGER).toHaveLength(5);
    expect(NAV_ITEMS.LOGISTICIAN).toHaveLength(1);
    expect(NAV_ITEMS.DRIVER).toHaveLength(1);
  });

  it('maps known roles to labels', () => {
    expect(roleLabel('DIRECTOR')).toBe('Директор магазина');
    expect(roleLabel('MANAGER')).toBe('Менеджер');
    expect(roleLabel('LOGISTICIAN')).toBe('Логист');
    expect(roleLabel('DRIVER')).toBe('Водитель');
  });

  it('returns fallback role label for unknown roles', () => {
    expect(roleLabel('ADMIN')).toBe('ADMIN');
  });

  it('returns section labels for known sections', () => {
    expect(sectionLabel('MANAGER', 'manager-orders')).toBe('Заявки');
    expect(sectionLabel('DIRECTOR', 'director-catalog')).toBe('Каталог и корзина');
    expect(sectionLabel('DRIVER', 'driver-orders')).toBe('Мои доставки');
  });

  it('returns empty section label for unknown section', () => {
    expect(sectionLabel('MANAGER', 'unknown')).toBe('');
    expect(sectionLabel('UNKNOWN', 'anything')).toBe('');
  });

  it('renders an icon element for known navigation item ids', () => {
    const { container } = render(itemIcon('manager-dashboard'));
    expect(container.querySelector('svg')).toBeTruthy();
  });

  it('falls back to default icon for unknown item ids', () => {
    const { container } = render(itemIcon('unknown-id'));
    expect(container.querySelector('svg')).toBeTruthy();
  });

  it('applies custom props to rendered icon', () => {
    const { container } = render(itemIcon('manager-dashboard', { 'data-testid': 'custom-icon' }));
    expect(container.querySelector('[data-testid=\"custom-icon\"]')).toBeTruthy();
  });
});
