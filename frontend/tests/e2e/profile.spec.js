import { test, expect } from './fixtures';

test.describe('Profile', () => {
  test('edits name and bio and keeps them after reload', async ({ page, user }) => {
    await page.goto('/profile');

    const name = page.getByLabel('Display name');
    const bio = page.getByLabel('Bio');
    await expect(name).toHaveValue(user.username);

    await name.fill('E2E Tester');
    await bio.fill('Bio written by an e2e test');
    await Promise.all([
      page.waitForResponse(res => res.url().endsWith('/api/profiles/me') && res.request().method() === 'PUT' && res.ok()),
      page.getByRole('button', { name: 'Save profile' }).click(),
    ]);
    await expect(page.getByText('Profile saved!')).toBeVisible();

    await page.reload();
    await expect(name).toHaveValue('E2E Tester');
    await expect(bio).toHaveValue('Bio written by an e2e test');
  });
});
