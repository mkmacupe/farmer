import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from './Sidebar.jsx';

const managerUser = {
  role: 'MANAGER',
  username: 'manager',
  fullName: 'Менеджер Менеджеров'
};

describe('Sidebar', () => {
  it('renders user identity and role label', () => {
    render(
      <Sidebar
        user={managerUser}
        activeSection="manager-dashboard"
        onNavigate={() => {}}
      />
    );

    expect(screen.getByText('Менеджер Менеджеров')).toBeInTheDocument();
    expect(screen.getByText('Менеджер')).toBeInTheDocument();
  });

  it('renders role-specific navigation items', () => {
    render(
      <Sidebar
        user={managerUser}
        activeSection="manager-dashboard"
        onNavigate={() => {}}
      />
    );

    expect(screen.getByRole('button', { name: /сводка/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /заявки/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /товары/i })).toBeInTheDocument();
  });

  it('calls onNavigate for selected item', async () => {
    const onNavigate = vi.fn();
    const uiUser = userEvent.setup();
    render(
      <Sidebar
        user={managerUser}
        activeSection="manager-dashboard"
        onNavigate={onNavigate}
      />
    );

    await uiUser.click(screen.getByRole('button', { name: /товары/i }));
    expect(onNavigate).toHaveBeenCalledWith('manager-products');
  });

  it('marks active section with aria-current', () => {
    render(
      <Sidebar
        user={managerUser}
        activeSection="manager-orders"
        onNavigate={() => {}}
      />
    );

    expect(screen.getByRole('button', { name: /заявки/i })).toHaveAttribute('aria-current', 'page');
  });

  it('calls onClose after navigation in mobile mode', async () => {
    const onNavigate = vi.fn();
    const onClose = vi.fn();
    const uiUser = userEvent.setup();
    render(
      <Sidebar
        user={managerUser}
        activeSection="manager-dashboard"
        onNavigate={onNavigate}
        isMobile
        mobileOpen
        onClose={onClose}
      />
    );

    await uiUser.click(screen.getByRole('button', { name: /товары/i }));
    expect(onNavigate).toHaveBeenCalledWith('manager-products');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('falls back to username when fullName is missing', () => {
    render(
      <Sidebar
        user={{ role: 'MANAGER', username: 'manager_only' }}
        activeSection="manager-dashboard"
        onNavigate={() => {}}
      />
    );

    expect(screen.getByText('manager_only')).toBeInTheDocument();
    expect(screen.getByText('M')).toBeInTheDocument();
  });

  it('renders empty navigation list for unknown role', () => {
    render(
      <Sidebar
        user={{ role: 'UNKNOWN', username: 'guest' }}
        activeSection="none"
        onNavigate={() => {}}
      />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByText('Навигация')).toBeInTheDocument();
  });

  it('uses U avatar fallback when both fullName and username are missing', () => {
    render(
      <Sidebar
        user={{ role: 'UNKNOWN' }}
        activeSection="none"
        onNavigate={() => {}}
      />
    );

    expect(screen.getByText('U')).toBeInTheDocument();
  });
});
