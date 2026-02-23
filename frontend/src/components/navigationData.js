export const NAV_ITEMS = {
  DIRECTOR: [
    { id: 'director-profile', label: 'Профиль' },
    { id: 'director-addresses', label: 'Адреса' },
    { id: 'director-catalog', label: 'Каталог' },
    { id: 'director-orders', label: 'История' }
  ],
  MANAGER: [
    { id: 'manager-dashboard', label: 'Сводка' },
    { id: 'manager-orders', label: 'Заявки' },
    { id: 'manager-products', label: 'Товары' },
    { id: 'manager-users', label: 'Пользователи' },
    { id: 'manager-reports', label: 'Отчёты' }
  ],
  LOGISTICIAN: [
    { id: 'logistic-orders', label: 'Назначения' }
  ],
  DRIVER: [
    { id: 'driver-orders', label: 'Доставки' }
  ]
};

export function roleLabel(role) {
  if (role === 'DIRECTOR') return 'Директор магазина';
  if (role === 'MANAGER') return 'Менеджер';
  if (role === 'LOGISTICIAN') return 'Логист';
  if (role === 'DRIVER') return 'Водитель';
  return role;
}

export function sectionLabel(role, sectionId) {
  const labels = {
    DIRECTOR: {
      'director-profile': 'Профиль',
      'director-addresses': 'Адреса',
      'director-catalog': 'Каталог и корзина',
      'director-orders': 'История заказов'
    },
    MANAGER: {
      'manager-dashboard': 'Сводка',
      'manager-orders': 'Заявки',
      'manager-products': 'Товары',
      'manager-users': 'Пользователи',
      'manager-reports': 'Отчёты'
    },
    LOGISTICIAN: {
      'logistic-orders': 'Назначения'
    },
    DRIVER: {
      'driver-orders': 'Мои доставки'
    }
  };

  return labels[role]?.[sectionId] || '';
}
