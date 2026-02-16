export const kpiData = [
  { title: 'Выручка', value: '45,231.89 BYN', trend: '+5% к прошлой неделе', status: 'up' },
  { title: 'Активные заказы', value: '24', trend: '12 в ожидании', status: 'neutral' },
  { title: 'Ожидают доставки', value: '8', trend: 'По графику', status: 'neutral' },
  { title: 'Низкий остаток', value: '3', trend: 'Требует внимания', status: 'warning' }
];

export const ordersData = [
  { id: '1001', customer: 'EcoMarket LLC', status: 'PENDING', date: '2023-10-25', total: '1,200.50 BYN' },
  { id: '1002', customer: 'Green Grocer', status: 'SHIPPED', date: '2023-10-24', total: '850.00 BYN' },
  { id: '1003', customer: 'Fresh Foods', status: 'DELIVERED', date: '2023-10-23', total: '2,100.00 BYN' },
  { id: '1004', customer: 'Local Bistro', status: 'PENDING', date: '2023-10-25', total: '340.20 BYN' },
  { id: '1005', customer: 'Market Chain A', status: 'DELIVERED', date: '2023-10-22', total: '5,600.00 BYN' },
];

export const inventoryData = [
  { id: 'p1', name: 'Potatoes (Gala)', stock: 150, unit: 'kg', threshold: 200 },
  { id: 'p2', name: 'Carrots', stock: 45, unit: 'kg', threshold: 100 },
  { id: 'p3', name: 'Milk (3.2%)', stock: 20, unit: 'L', threshold: 50 },
];
