import { test, expect } from './fixtures';

test.describe('Ask AI', () => {
  test('returns recommendations for a genre', async ({ page, user }) => {
    await page.goto('/ask');

    await page.getByRole('button', { name: 'Drama', exact: true }).click();
    await Promise.all([
      page.waitForResponse(res => res.url().endsWith('/api/recommendations/quick') && res.ok()),
      page.getByRole('button', { name: 'Get recommendations' }).click(),
    ]);

    await expect(page.locator('.result .movie-card').first()).toBeVisible();
  });
});
