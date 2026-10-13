import { test as base, expect } from '@playwright/test';

export const PASSWORD = 'password123';

export function uniqueUsername() {
  return `e2e_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
}

export async function createUser(request, username = uniqueUsername()) {
  const reg = await request.post('/api/auth/register', { data: { username, password: PASSWORD } });
  expect(reg.ok()).toBeTruthy();
  const login = await request.post('/api/auth/login', { data: { username, password: PASSWORD } });
  expect(login.ok()).toBeTruthy();
  return login.json();
}

export const test = base.extend({
  page: async ({ page }, use) => {
    // some weird issue that webkit freezes with backdrop-filter
    await page.addInitScript(() => {
      document.addEventListener('DOMContentLoaded', () => {
        const style = document.createElement('style');
        style.textContent = '*, *::before, *::after { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; }';
        document.head.appendChild(style);
      });
    });
    await use(page);
  },
  user: async ({ page, request }, use) => {
    const user = await createUser(request);
    await page.addInitScript((u) => {
      window.localStorage.setItem('auth', JSON.stringify({ token: u.token, userId: u.userId, username: u.username }));
    }, user);
    await use(user);
  },
});

export { expect };
