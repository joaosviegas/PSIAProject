import { test, expect } from '@playwright/test';

test.describe('Movies Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/profiles/me', async (route) => {
      await route.fulfill({
        status: 200,
        json: { id: 1, name: 'Test User', email: 'test@example.com' }
      });
    });

    await page.addInitScript(() => {
      window.localStorage.setItem('jwt', 'fake-test-token');
    });
  });
});