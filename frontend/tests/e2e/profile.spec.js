import { test, expect } from '@playwright/test';

test.describe('Profile Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('jwt', 'fake-test-token');
    });
  });
});