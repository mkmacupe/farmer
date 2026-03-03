import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import {
  AddressCardSkeleton,
  DashboardSkeleton,
  NotificationSkeleton,
  OrderTableSkeleton,
  ProductCardSkeleton,
  ProductGridSkeleton,
  ProfileSkeleton
} from './LoadingSkeletons.jsx';

describe('LoadingSkeletons', () => {
  it('renders single product card skeleton', () => {
    const { container } = render(<ProductCardSkeleton />);
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('.MuiCard-root').length).toBe(1);
  });

  it('renders product grid skeleton with default count', () => {
    const { container } = render(<ProductGridSkeleton />);
    expect(container.querySelectorAll('.MuiCard-root').length).toBe(6);
  });

  it('renders product grid skeleton with custom count', () => {
    const { container } = render(<ProductGridSkeleton count={2} />);
    expect(container.querySelectorAll('.MuiCard-root').length).toBe(2);
  });

  it('renders order table skeleton with custom rows', () => {
    const { container } = render(<OrderTableSkeleton rows={2} />);
    expect(container.querySelectorAll('tr').length).toBe(3);
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
  });

  it('renders dashboard skeleton layout', () => {
    const { container } = render(<DashboardSkeleton />);
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('.MuiPaper-root').length).toBeGreaterThanOrEqual(5);
  });

  it('renders profile skeleton layout', () => {
    const { container } = render(<ProfileSkeleton />);
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('.MuiPaper-root').length).toBe(1);
  });

  it('renders address card skeleton', () => {
    const { container } = render(<AddressCardSkeleton />);
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('.MuiCard-root').length).toBe(1);
  });

  it('renders notification skeleton with default and custom count', () => {
    const { container: defaultContainer } = render(<NotificationSkeleton />);
    expect(defaultContainer.querySelectorAll('.MuiPaper-root').length).toBe(3);

    const { container: customContainer } = render(<NotificationSkeleton count={1} />);
    expect(customContainer.querySelectorAll('.MuiPaper-root').length).toBe(1);
  });
});
