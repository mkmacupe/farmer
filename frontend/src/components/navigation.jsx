import React from 'react';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import GridViewOutlinedIcon from '@mui/icons-material/GridViewOutlined';
import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import RouteOutlinedIcon from '@mui/icons-material/RouteOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';

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

const ICON_MAP = {
  'director-profile': PersonOutlineIcon,
  'director-addresses': LocationOnOutlinedIcon,
  'director-catalog': ShoppingCartOutlinedIcon,
  'director-orders': HistoryOutlinedIcon,
  'manager-dashboard': GridViewOutlinedIcon,
  'manager-orders': AssignmentOutlinedIcon,
  'manager-products': Inventory2OutlinedIcon,
  'manager-users': GroupOutlinedIcon,
  'manager-reports': DescriptionOutlinedIcon,
  'logistic-orders': RouteOutlinedIcon,
  'driver-orders': LocalShippingOutlinedIcon
};

export function itemIcon(id, props = {}) {
  const Icon = ICON_MAP[id] || AssignmentOutlinedIcon;
  return <Icon fontSize="small" {...props} />;
}

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
