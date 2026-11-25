import { expect, test as setup } from '@playwright/test'

const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@sensonet.local'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? ''

setup('iniciar sesión como administrador', async ({ page }) => {
  await page.goto('./')
  await page.getByLabel('Correo').fill(ADMIN_EMAIL)
  await page.getByLabel('Contraseña').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  await expect(page.getByRole('heading', { name: 'Inicio' })).toBeVisible()
  await page.context().storageState({ path: '.auth/admin.json' })
})
