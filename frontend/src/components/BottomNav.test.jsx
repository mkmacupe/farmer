import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import BottomNav from './BottomNav.jsx';

describe('BottomNav', () => {
  it('renders nothing for unknown role', () => {
    const { container } = render(
      <BottomNav role="UNKNOWN" activeSection="none" onNavigate={() => {}} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders director navigation actions', () => {
    render(
      <BottomNav role="DIRECTOR" activeSection="director-profile" onNavigate={() => {}} />
    );

    expect(screen.getByRole('button', { name: /профиль/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /адреса/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /каталог/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /история/i })).toBeInTheDocument();
  });

  it('keeps active section selected', () => {
    render(
      <BottomNav role="DIRECTOR" activeSection="director-catalog" onNavigate={() => {}} />
    );

    const activeButton = screen.getByRole('button', { name: /каталог/i });
    expect(activeButton.className).toContain('Mui-selected');
  });

  it('calls onNavigate when action is clicked', async () => {
    const onNavigate = vi.fn();
    const uiUser = userEvent.setup();

    render(
      <BottomNav role="DIRECTOR" activeSection="director-profile" onNavigate={onNavigate} />
    );

    await uiUser.click(screen.getByRole('button', { name: /каталог/i }));
    expect(onNavigate).toHaveBeenCalledWith('director-catalog');
  });
});
