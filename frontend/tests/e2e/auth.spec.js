import { test, expect } from '@playwright/test';

test.describe('Authentication & Navigation', () => {
  const username = `testuser_${Date.now()}`;
  const password = 'password123';

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
    await page.getByRole('button', { name: 'Register' }).click();
    
    // Assuming successful registration redirects to login or auto-logs in.
    // The current UI shows a link to login, so we'll test the manual flow if it doesn't auto-route.
    await page.goto('/login');
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill(password);
    
    // Loading state check
    const loginBtn = page.getByRole('button', { name: 'Login' });
    await loginBtn.click();
    await expect(page.getByRole('button', { name: 'Logging in...' })).toBeVisible();

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