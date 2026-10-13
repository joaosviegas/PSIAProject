import { test, expect, createUser } from './fixtures';

test.describe('People', () => {
  test('finds another user and opens their profile', async ({ page, request, user }) => {
    const other = await createUser(request);
    const saved = await request.put('/api/profiles/me', {
      headers: { Authorization: `Bearer ${other.token}` },
      data: { displayName: `Other ${other.username}`, bio: 'Other user bio', favoriteGenres: ['Drama'], favoriteMovieIds: [], dislikedMovieIds: [] },
    });
    expect(saved.ok()).toBeTruthy();

    await page.goto('/people');
    await Promise.all([
      page.waitForResponse(res => res.url().includes(`q=${other.username}`) && res.ok()),
      page.getByPlaceholder('Search by name...').fill(other.username),
    ]);

    await page.getByRole('heading', { name: `Other ${other.username}` }).click();
    await expect(page).toHaveURL(`/people/${other.userId}`);
    await expect(page.getByRole('heading', { level: 1, name: `Other ${other.username}` })).toBeVisible();
    await expect(page.getByText('Other user bio')).toBeVisible();
  });
});
