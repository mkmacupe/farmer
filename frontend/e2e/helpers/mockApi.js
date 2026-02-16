function nowIso(fixedNow) {
  return fixedNow || new Date().toISOString();
}

export async function installApiMock(page, options = {}) {
  const state = {
    nextOrderId: 400,
    products: [
      {
        id: 1,
        name: 'Молоко 1 л',
        category: 'Молочная продукция',
        description: 'Свежее молоко',
        photoUrl: 'https://example.com/milk.jpg',
        price: 45.0,
        stockQuantity: 30
      }
    ],
    addresses: [
      {
        id: 10,
        label: 'Main Store',
        addressLine: 'Kyiv, Khreshchatyk 1',
        latitude: 50.4501,
        longitude: 30.5234
      }
    ],
    directors: [
      {
        id: 11,
        username: 'director',
        fullName: 'Director User',
        phone: '+380500000001',
        legalEntityName: 'Fresh Retail LLC',
        role: 'DIRECTOR'
      }
    ],
    drivers: [
      {
        id: 31,
        username: 'driver1',
        fullName: 'Driver One',
        phone: '+380500000004',
        legalEntityName: null,
        role: 'DRIVER'
      },
      {
        id: 32,
        username: 'driver2',
        fullName: 'Driver Two',
        phone: '+380500000005',
        legalEntityName: null,
        role: 'DRIVER'
      },
      {
        id: 33,
        username: 'driver3',
        fullName: 'Driver Three',
        phone: '+380500000006',
        legalEntityName: null,
        role: 'DRIVER'
      }
    ],
    orders: [
      {
        id: 301,
        customerId: 11,
        customerName: 'Director User',
        deliveryAddressId: 10,
        deliveryAddressText: 'Kyiv, Khreshchatyk 1',
        deliveryLatitude: 50.4501,
        deliveryLongitude: 30.5234,
        assignedDriverId: null,
        assignedDriverName: null,
        status: 'CREATED',
        createdAt: nowIso(options.fixedNow),
        updatedAt: nowIso(options.fixedNow),
        approvedAt: null,
        assignedAt: null,
        deliveredAt: null,
        totalAmount: 90.0,
        items: [{ productId: 1, productName: 'Молоко 1 л', quantity: 2, price: 45.0, lineTotal: 90.0 }]
      },
      {
        id: 302,
        customerId: 11,
        customerName: 'Director User',
        deliveryAddressId: 10,
        deliveryAddressText: 'Kyiv, Khreshchatyk 1',
        deliveryLatitude: 50.4501,
        deliveryLongitude: 30.5234,
        assignedDriverId: null,
        assignedDriverName: null,
        status: 'APPROVED',
        createdAt: nowIso(options.fixedNow),
        updatedAt: nowIso(options.fixedNow),
        approvedAt: nowIso(options.fixedNow),
        assignedAt: null,
        deliveredAt: null,
        totalAmount: 45.0,
        items: [{ productId: 1, productName: 'Молоко 1 л', quantity: 1, price: 45.0, lineTotal: 45.0 }]
      },
      {
        id: 303,
        customerId: 11,
        customerName: 'Director User',
        deliveryAddressId: 10,
        deliveryAddressText: 'Kyiv, Khreshchatyk 1',
        deliveryLatitude: 50.4501,
        deliveryLongitude: 30.5234,
        assignedDriverId: 31,
        assignedDriverName: 'Driver One',
        status: 'ASSIGNED',
        createdAt: nowIso(options.fixedNow),
        updatedAt: nowIso(options.fixedNow),
        approvedAt: nowIso(options.fixedNow),
        assignedAt: nowIso(options.fixedNow),
        deliveredAt: null,
        totalAmount: 45.0,
        items: [{ productId: 1, productName: 'Молоко 1 л', quantity: 1, price: 45.0, lineTotal: 45.0 }]
      }
    ]
  };

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const token = request.headers().authorization?.replace('Bearer ', '') || '';
    const tokenUser = token.replace('token-', '');

    if (path.endsWith('/api/auth/login') && method === 'POST') {
      const payload = JSON.parse(request.postData() || '{}');
      const username = String(payload.username || '').trim().toLowerCase();
      const roles = {
        director: 'DIRECTOR',
        manager: 'MANAGER',
        logistician: 'LOGISTICIAN',
        driver1: 'DRIVER',
        driver2: 'DRIVER',
        driver3: 'DRIVER'
      };
      if (!roles[username]) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Invalid credentials' })
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: `token-${username}`,
          username,
          fullName: username[0].toUpperCase() + username.slice(1),
          role: roles[username]
        })
      });
      return;
    }

    if (path.endsWith('/api/notifications/stream') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: 'event: connected\ndata: {"type":"CONNECTED"}\n\n'
      });
      return;
    }

    if (path.endsWith('/api/products/categories') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['Молочная продукция'])
      });
      return;
    }

    if (path.endsWith('/api/products') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.products)
      });
      return;
    }

    if (path.endsWith('/api/director/profile') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 11,
          username: 'director',
          fullName: 'Director User',
          phone: '+380500000001',
          legalEntityName: 'Fresh Retail LLC'
        })
      });
      return;
    }

    if (path.endsWith('/api/director/addresses') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.addresses)
      });
      return;
    }

    if (path.endsWith('/api/orders/my') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(tokenUser === 'director'
          ? state.orders.filter((order) => order.customerId === 11)
          : [])
      });
      return;
    }

    if (path.endsWith('/api/orders/assigned') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(tokenUser === 'driver1'
          ? state.orders.filter((order) => order.assignedDriverId === 31)
          : [])
      });
      return;
    }

    if (path.endsWith('/api/orders') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.orders)
      });
      return;
    }

    if (path.endsWith('/api/orders') && method === 'POST') {
      const payload = JSON.parse(request.postData() || '{}');
      const items = payload.items || [];
      const total = items.reduce((sum, item) => sum + Number(item.quantity || 0) * 45.0, 0);
      const created = {
        id: state.nextOrderId++,
        customerId: 11,
        customerName: 'Director User',
        deliveryAddressId: Number(payload.deliveryAddressId || 10),
        deliveryAddressText: 'Kyiv, Khreshchatyk 1',
        deliveryLatitude: 50.4501,
        deliveryLongitude: 30.5234,
        assignedDriverId: null,
        assignedDriverName: null,
        status: 'CREATED',
        createdAt: nowIso(options.fixedNow),
        updatedAt: nowIso(options.fixedNow),
        approvedAt: null,
        assignedAt: null,
        deliveredAt: null,
        totalAmount: total,
        items: items.map((item) => ({
          productId: item.productId,
          productName: 'Молоко 1 л',
          quantity: Number(item.quantity),
          price: 45.0,
          lineTotal: Number(item.quantity) * 45.0
        }))
      };
      state.orders.unshift(created);
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created)
      });
      return;
    }

    if (path.match(/\/api\/orders\/\d+\/approve$/) && method === 'POST') {
      const id = Number(path.split('/').at(-2));
      state.orders = state.orders.map((order) => (
        order.id === id
          ? { ...order, status: 'APPROVED', approvedAt: nowIso(options.fixedNow), updatedAt: nowIso(options.fixedNow) }
          : order
      ));
      const updated = state.orders.find((order) => order.id === id);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(updated) });
      return;
    }

    if (path.match(/\/api\/orders\/\d+\/assign-driver$/) && method === 'POST') {
      const id = Number(path.split('/').at(-2));
      const payload = JSON.parse(request.postData() || '{}');
      state.orders = state.orders.map((order) => (
        order.id === id
          ? {
            ...order,
            status: 'ASSIGNED',
            assignedDriverId: Number(payload.driverId),
            assignedDriverName: 'Driver One',
            assignedAt: nowIso(options.fixedNow),
            updatedAt: nowIso(options.fixedNow)
          }
          : order
      ));
      const updated = state.orders.find((order) => order.id === id);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(updated) });
      return;
    }

    if (path.match(/\/api\/orders\/\d+\/deliver$/) && method === 'POST') {
      const id = Number(path.split('/').at(-2));
      state.orders = state.orders.map((order) => (
        order.id === id
          ? { ...order, status: 'DELIVERED', deliveredAt: nowIso(options.fixedNow), updatedAt: nowIso(options.fixedNow) }
          : order
      ));
      const updated = state.orders.find((order) => order.id === id);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(updated) });
      return;
    }

    if (path.match(/\/api\/orders\/\d+\/timeline$/) && method === 'GET') {
      const id = Number(path.split('/').at(-2));
      const order = state.orders.find((candidate) => candidate.id === id);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(order
          ? [{
            id: 1,
            orderId: id,
            fromStatus: null,
            toStatus: order.status,
            actorUsername: 'system',
            actorUserId: 1,
            actorRole: 'MANAGER',
            details: 'Status updated',
            createdAt: nowIso(options.fixedNow)
          }]
          : [])
      });
      return;
    }

    if (path.endsWith('/api/users/drivers') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.drivers)
      });
      return;
    }

    if (path.endsWith('/api/users/directors') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.directors)
      });
      return;
    }

    if (path.endsWith('/api/users/directors') && method === 'POST') {
      const payload = JSON.parse(request.postData() || '{}');
      const created = {
        id: 100 + state.directors.length,
        username: payload.username,
        fullName: payload.fullName,
        phone: payload.phone,
        legalEntityName: payload.legalEntityName,
        role: 'DIRECTOR'
      };
      state.directors.push(created);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) });
      return;
    }

    if (path.endsWith('/api/dashboard/summary') && method === 'GET') {
      const deliveredOrders = state.orders.filter((order) => order.status === 'DELIVERED').length;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          from: null,
          to: null,
          totalOrders: state.orders.length,
          deliveredOrders,
          totalRevenue: state.orders
            .filter((order) => order.status === 'DELIVERED')
            .reduce((sum, order) => sum + Number(order.totalAmount), 0),
          averageCheck: state.orders.length
            ? state.orders.reduce((sum, order) => sum + Number(order.totalAmount), 0) / state.orders.length
            : 0,
          ordersByStatus: [
            { status: 'CREATED', count: state.orders.filter((order) => order.status === 'CREATED').length },
            { status: 'APPROVED', count: state.orders.filter((order) => order.status === 'APPROVED').length },
            { status: 'ASSIGNED', count: state.orders.filter((order) => order.status === 'ASSIGNED').length },
            { status: 'DELIVERED', count: deliveredOrders }
          ]
        })
      });
      return;
    }

    if (path.endsWith('/api/reports/orders') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        body: 'mock-report'
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ message: `No mock route for ${method} ${path}` })
    });
  });
}

export async function loginAs(page, username) {
  await page.goto('/');
  await page.getByLabel(/логин/i).fill(username);
  await page.getByLabel(/пароль/i).fill('StrongDemoPass123!');
  await page.getByRole('button', { name: /войти/i }).click();
}
