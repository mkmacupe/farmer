export const kpiData = [
  { title: 'Общая выручка', value: '45,231.89 BYN', trend: '+5% к прошлой неделе', status: 'up' },
  { title: 'Активные заказы', value: '24', trend: '12 в ожидании', status: 'neutral' },
  { title: 'Ожидают доставки', value: '8', trend: 'По графику', status: 'neutral' },
  { title: 'Заканчиваются', value: '3', trend: 'Требует внимания', status: 'warning' }
];

export const ordersData = [
  { id: '1001', customer: 'ООО ЭкоМаркет', status: 'PENDING', date: '25.10.2023', total: '1,200.50 BYN' },
  { id: '1002', customer: 'Зеленая Лавка', status: 'SHIPPED', date: '24.10.2023', total: '850.00 BYN' },
  { id: '1003', customer: 'Свежие Продукты', status: 'DELIVERED', date: '23.10.2023', total: '2,100.00 BYN' },
  { id: '1004', customer: 'Бистро "У дома"', status: 'PENDING', date: '25.10.2023', total: '340.20 BYN' },
  { id: '1005', customer: 'Торговая Сеть А', status: 'DELIVERED', date: '22.10.2023', total: '5,600.00 BYN' },
];

export const inventoryData = [
  { id: 'p1', name: 'Картофель (Гала)', stock: 150, unit: 'кг', threshold: 200 },
  { id: 'p2', name: 'Морковь', stock: 45, unit: 'кг', threshold: 100 },
  { id: 'p3', name: 'Молоко (3.2%)', stock: 20, unit: 'л', threshold: 50 },
];
