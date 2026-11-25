import { expect, test } from '@playwright/test'

// Regenera las capturas del README: SCREENSHOTS=1 npx playwright test screenshots
test.skip(!process.env.SCREENSHOTS, 'solo con SCREENSHOTS=1')
test.use({ viewport: { width: 1360, height: 900 } })

const OUT = '../docs/img'

test('capturas del dashboard', async ({ page }) => {
  await page.goto('./')
  await expect(page.getByText('En vivo')).toBeVisible()
  await expect(page.getByRole('link', { name: /sim-sala/ }).first()).toBeVisible()
  await page.waitForTimeout(1500)
  await page.screenshot({ path: `${OUT}/inicio.png` })

  await page.getByRole('link', { name: /sim-sala/ }).first().click()
  await expect(page.getByRole('img', { name: 'Gráfica de Temperatura', exact: true }).first()).toBeVisible()
  await page.waitForTimeout(1500)
  await page.screenshot({ path: `${OUT}/detalle-nodo.png`, fullPage: true })

  await page.getByRole('link', { name: 'Alertas' }).click()
  await page.getByRole('tab', { name: 'Todas' }).click()
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${OUT}/alertas.png` })

  await page.getByRole('link', { name: 'Reglas' }).click()
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${OUT}/reglas.png` })
})
