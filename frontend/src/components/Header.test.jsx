import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Header from './Header.jsx';

const user = {
  role: 'DIRECTOR',
  username: 'director01',
  fullName: 'Директор магазина 01'
};

describe('Header', () => {
  it('shows section title when active section is known', () => {
    render(
      <Header
        user={user}
        activeSection="director-profile"
        onLogout={() => {}}
        onToggleSidebar={() => {}}
      />
    );

    expect(screen.getByRole('heading', { name: 'Профиль' })).toBeInTheDocument();
  });

  it('falls back to default title for unknown section', () => {
    render(
      <Header
        user={user}
        activeSection="unknown-section"
        onLogout={() => {}}
        onToggleSidebar={() => {}}
      />
    );

    expect(screen.getByRole('heading', { name: 'Рабочий кабинет' })).toBeInTheDocument();
  });

  it('hides menu button by default', () => {
    render(
      <Header
        user={user}
        activeSection="director-profile"
        onLogout={() => {}}
        onToggleSidebar={() => {}}
      />
    );

    expect(screen.queryByRole('button', { name: /открыть меню/i })).toBeNull();
  });

  it('calls onToggleSidebar when menu button is clicked', async () => {
    const onToggleSidebar = vi.fn();
    const uiUser = userEvent.setup();
    render(
      <Header
        user={user}
        activeSection="director-profile"
        onLogout={() => {}}
        onToggleSidebar={onToggleSidebar}
        showMenuButton
      />
    );

    await uiUser.click(screen.getByRole('button', { name: /открыть меню/i }));
    expect(onToggleSidebar).toHaveBeenCalledTimes(1);
  });

  it('calls onLogout when logout button is clicked', async () => {
    const onLogout = vi.fn();
    const uiUser = userEvent.setup();
    render(
      <Header
        user={user}
        activeSection="director-profile"
        onLogout={onLogout}
        onToggleSidebar={() => {}}
      />
    );

    await uiUser.click(screen.getByRole('button', { name: /выйти/i }));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});
