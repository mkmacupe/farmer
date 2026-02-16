import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import OrdersTable from './OrdersTable.jsx';

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
    expect(screen.getByRole('button', { name: 'Action 101' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Action 102' })).toBeInTheDocument();
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
});
