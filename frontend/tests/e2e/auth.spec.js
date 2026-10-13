import { test, expect } from '@playwright/test';

test.describe('Authentication & Navigation', () => {
  let username;
  const password = 'password123';

  test.beforeEach(() => {
    // Generate a fresh unique username for each test run
    username = `testuser_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  });

  test('prevents access to protected routes when logged out', async ({ page }) => {
    await page.goto('/profile');
    // Vue router should redirect to login
    await expect(page).toHaveURL('/login');
  });

  test('registers a new user and logs in', async ({ page }) => {
    await page.goto('/register');
    
    // Fill registration form
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill(password);
    
    // Submit registration and wait for the API response to complete
    await Promise.all([
      page.waitForResponse(res => res.url().includes('/auth') && (res.status() === 200 || res.status() === 201)),
      page.getByRole('button', { name: 'Register' }).click()
    ]);
    
    // Navigate to login if the UI didn't auto-redirect
    if (!page.url().includes('/login')) {
      await page.goto('/login');
    }

    // Fill login form
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill(password);
    
    // Submit login and wait for the successful response
    await Promise.all([
      page.waitForResponse(res => res.url().includes('/auth') && res.status() === 200),
      page.getByRole('button', { name: 'Login' }).click()
    ]);

    // Successful login redirects to Feed (/)
    await expect(page).toHaveURL('/');
    
    // Verify auth state persistence
    await page.reload();
    await expect(page).toHaveURL('/');
  });

  test('displays API errors on invalid login', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('nonexistent_user');
    await page.getByLabel('Password').fill('wrongpass');
    await page.getByRole('button', { name: 'Login' }).click();
    
    // The UI binds error to a <p class="error">
    const errorMsg = page.locator('.error');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).not.toBeEmpty();
  });
});