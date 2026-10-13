import { test, expect } from './fixtures';

test.describe('Feed', () => {
  test('new user picks categories and gets a feed', async ({ page, user }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'What do you like to watch?' })).toBeVisible();
    const build = page.getByRole('button', { name: /Build my feed/ });
    await expect(build).toBeDisabled();

    await page.getByRole('button', { name: 'Drama', exact: true }).click();
    await expect(build).toHaveText('Build my feed (1)');

    await Promise.all([
      page.waitForResponse(res => res.url().endsWith('/api/profiles/me') && res.request().method() === 'PUT' && res.ok()),
      build.click(),
    ]);

    await expect(page.getByRole('heading', { name: /^(AI|Smart) picks$/ })).toBeVisible();
    await expect(page.locator('.movie-card').first()).toBeVisible();
  });
});
