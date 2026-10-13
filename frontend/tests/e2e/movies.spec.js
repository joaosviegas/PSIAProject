import { test, expect } from './fixtures';

test.describe('Movies', () => {
  test('searches, filters by genre and opens a movie', async ({ page, user }) => {
    await page.goto('/movies');
    await expect(page.locator('.movie-card').first()).toBeVisible();

    await Promise.all([
      page.waitForResponse(res => res.url().includes('/api/movies?') && res.url().includes('query=Inception') && res.ok()),
      page.getByPlaceholder('Search title, director, actor...').fill('Inception'),
    ]);
    await Promise.all([
      page.waitForResponse(res => res.url().includes('genre=Sci-Fi') && res.ok()),
      page.locator('select.filter-select').first().selectOption('Sci-Fi'),
    ]);

    const card = page.locator('.movie-card', { has: page.getByRole('heading', { name: 'Inception', exact: true }) });
    await expect(card).toBeVisible();

    await card.getByRole('heading', { name: 'Inception', exact: true }).click();
    const modal = page.getByRole('dialog', { name: 'Inception' });
    await expect(modal).toBeVisible();
    await expect(modal.getByText('Christopher Nolan')).toBeVisible();

    await modal.getByTitle('Close').click();
    await expect(modal).toBeHidden();
  });

  test('liked movie shows up on the profile', async ({ page, user }) => {
    await page.goto('/movies');
    await Promise.all([
      page.waitForResponse(res => res.url().includes('query=Inception') && res.ok()),
      page.getByPlaceholder('Search title, director, actor...').fill('Inception'),
    ]);

    const card = page.locator('.movie-card', { has: page.getByRole('heading', { name: 'Inception', exact: true }) });
    await Promise.all([
      page.waitForResponse(res => res.url().endsWith('/api/profiles/me') && res.request().method() === 'PUT' && res.ok()),
      card.getByTitle('Like', { exact: true }).click(),
    ]);
    await expect(card.getByTitle('Remove like')).toHaveClass(/active/);

    await page.goto('/profile');
    const liked = page.locator('.movie-list', { has: page.getByRole('heading', { name: 'Liked movies' }) });
    await expect(liked.getByRole('heading', { name: /^Inception/ })).toBeVisible();
  });
});
