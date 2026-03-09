import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrdersTable from './OrdersTable.jsx';

const useMediaQueryMock = vi.fn();

vi.mock('@mui/material/useMediaQuery', () => ({
  default: (...args) => useMediaQueryMock(...args)
}));

const orders = [
  {
    id: 101,
    customerName: 'Иван Петров',
    deliveryAddressText: 'Могилёв, ул. Черепова 5',
    assignedDriverName: 'Водитель 1',
    status: 'CREATED',
    createdAt: '2026-02-10T10:00:00Z',
    totalAmount: 12.5,
    items: [{ id: 1 }]
  },
  {
    id: 102,
    customerName: 'Мария Соколова',
    deliveryAddressText: 'Могилёв, ул. Ленина 20',
    assignedDriverName: null,
    status: 'APPROVED',
    createdAt: '2026-02-11T11:00:00Z',
    totalAmount: 20,
    items: [{ id: 2 }, { id: 3 }]
  },
  {
    id: 103,
    customerName: 'Павел Орлов',
    deliveryAddressText: 'Могилёв, пр-т Мира 8',
    assignedDriverName: 'Водитель 3',
    status: 'DELIVERED',
    createdAt: '2026-02-12T12:00:00Z',
    totalAmount: 33.1,
    items: [{ id: 4 }]
  }
];

async function pickStatus(label) {
  const uiUser = userEvent.setup();
  await uiUser.click(screen.getByLabelText('Статус'));
  const listbox = await screen.findByRole('listbox');
  await uiUser.click(within(listbox).getByText(label));
}

describe('OrdersTable', () => {
  beforeEach(() => {
    useMediaQueryMock.mockReturnValue(false);
  });

  it('renders empty state for no orders', () => {
    render(<OrdersTable orders={[]} emptyText="Нет данных" />);
    expect(screen.getByText('Заказов пока нет')).toBeInTheDocument();
    expect(screen.getByText('Нет данных')).toBeInTheDocument();
  });

  it('hides filters for a single order', () => {
    render(<OrdersTable orders={[orders[0]]} />);
    expect(screen.queryByLabelText('Статус')).toBeNull();
    expect(screen.getByText('#101')).toBeInTheDocument();
  });

  it('renders actions from actionRenderer', () => {
    render(
      <OrdersTable
        orders={orders}
        actionRenderer={(order) => <button type="button">Action {order.id}</button>}
      />
    );
    expect(screen.getByText('Action 101')).toBeInTheDocument();
    expect(screen.getByText('Action 102')).toBeInTheDocument();
  });

  it('filters rows by search term', async () => {
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'Соколова');

    expect(screen.getByText('#102')).toBeInTheDocument();
    expect(screen.queryByText('#101')).toBeNull();
    expect(screen.queryByText('#103')).toBeNull();
  });

  it('filters rows by selected status', async () => {
    render(<OrdersTable orders={orders} />);
    await pickStatus('Одобрен');

    expect(screen.getByText('#102')).toBeInTheDocument();
    expect(screen.queryByText('#101')).toBeNull();
    expect(screen.queryByText('#103')).toBeNull();
  });

  it('resets active filters with clear button', async () => {
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'Соколова');
    expect(screen.queryByText('#101')).toBeNull();
    expect(screen.queryByText('#103')).toBeNull();

    await uiUser.click(screen.getByRole('button', { name: /сбросить/i }));

    expect(screen.getByText('#101')).toBeInTheDocument();
    expect(screen.getByText('#102')).toBeInTheDocument();
    expect(screen.getByText('#103')).toBeInTheDocument();
  });

  it('hides customer column when showCustomer is false', () => {
    render(<OrdersTable orders={orders} showCustomer={false} />);
    expect(screen.queryByText('Директор')).toBeNull();
  });

  it('renders loading skeleton state', () => {
    const { container } = render(<OrdersTable orders={orders} loading />);
    expect(container.querySelector('.MuiSkeleton-root')).toBeTruthy();
  });

  it('clears search by clear icon button', async () => {
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'Соколова');
    expect(screen.queryByText('#101')).toBeNull();
    await uiUser.click(screen.getByRole('button', { name: /очистить поиск/i }));
    expect(screen.getByText('#101')).toBeInTheDocument();
    expect(screen.getByText('#102')).toBeInTheDocument();
  });

  it('shows fallback values for invalid money and date', () => {
    render(
      <OrdersTable
        orders={[{
          ...orders[0],
          id: 999,
          totalAmount: 'not-a-number',
          createdAt: 'invalid-date'
        }]}
      />
    );

    expect(screen.getByText('invalid-date')).toBeInTheDocument();
    expect(screen.getByText('0.00')).toBeInTheDocument();
  });

  it('shows desktop no-results state and resets filters', async () => {
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'совпадений-нет');
    expect(screen.getByText('По фильтрам ничего не найдено')).toBeInTheDocument();
    await uiUser.click(screen.getByRole('button', { name: /сбросить фильтры/i }));
    expect(screen.getByText('#101')).toBeInTheDocument();
  });

  it('renders desktop fallbacks for unknown status, empty address and missing items', () => {
    render(
      <OrdersTable
        orders={[{
          ...orders[0],
          id: 666,
          status: 'IN_PROGRESS',
          deliveryAddressText: null,
          createdAt: null,
          items: undefined
        }]}
      />
    );

    expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('0', { selector: 'span.MuiChip-label' })).toBeInTheDocument();
  });

  it('renders mobile cards and action renderer', () => {
    useMediaQueryMock.mockReturnValue(true);
    render(
      <OrdersTable
        orders={orders}
        actionRenderer={(order) => <button type="button">Mobile Action {order.id}</button>}
      />
    );

    expect(screen.getByText('Заказ #101')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mobile Action 101' })).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('shows no-results state in mobile mode and resets filters', async () => {
    useMediaQueryMock.mockReturnValue(true);
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'нет-совпадений');
    expect(screen.getByText('По фильтрам ничего не найдено')).toBeInTheDocument();
    await uiUser.click(screen.getByRole('button', { name: /сбросить фильтры/i }));
    expect(screen.getByText('Заказ #101')).toBeInTheDocument();
  });

  it('uses raw status text when status metadata is unknown', () => {
    useMediaQueryMock.mockReturnValue(true);
    render(
      <OrdersTable
        orders={[{
          ...orders[0],
          id: 777,
          status: 'IN_PROGRESS'
        }]}
      />
    );

    expect(screen.getByText('IN_PROGRESS')).toBeInTheDocument();
  });

  it('search falls back to raw unknown status text', async () => {
    const uiUser = userEvent.setup();
    render(
      <OrdersTable
        orders={[
          orders[0],
          { ...orders[1], id: 7777, status: 'IN_PROGRESS' }
        ]}
      />
    );

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'in_progress');
    expect(screen.getByText('#7777')).toBeInTheDocument();
    expect(screen.queryByText('#101')).toBeNull();
  });

  it('formats zero money values through fallback branch', () => {
    render(
      <OrdersTable
        orders={[{
          ...orders[0],
          id: 1001,
          totalAmount: null
        }]}
      />
    );

    expect(screen.getByText(/^0[,.]00$/)).toBeInTheDocument();
  });

  it('hides customer and uses mobile fallbacks for empty values', () => {
    useMediaQueryMock.mockReturnValue(true);
    render(
      <OrdersTable
        showCustomer={false}
        orders={[{
          ...orders[0],
          id: 555,
          deliveryAddressText: null,
          items: null
        }]}
      />
    );

    expect(screen.queryByText('Иван Петров')).toBeNull();
    expect(screen.getByText('Заказ #555')).toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('0', { selector: 'span.MuiChip-label' })).toBeInTheDocument();
  });

  it('shows customer fallback dash in mobile cards', () => {
    useMediaQueryMock.mockReturnValue(true);
    render(
      <OrdersTable
        orders={[{
          ...orders[0],
          id: 444,
          customerName: null
        }]}
      />
    );

    expect(screen.getByText('Заказ #444')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('searches by status label text produced by statusLabel helper', async () => {
    const uiUser = userEvent.setup();
    render(<OrdersTable orders={orders} />);

    await uiUser.type(screen.getByPlaceholderText('Поиск по заказам...'), 'доставлен');
    expect(screen.getByText('#103')).toBeInTheDocument();
    expect(screen.queryByText('#101')).toBeNull();
  });
});
