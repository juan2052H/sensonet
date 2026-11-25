import { expect, test, type Page } from '@playwright/test'

const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@sensonet.local'

async function login(page: Page) {
  await page.goto('./')
  await expect(page.getByRole('heading', { name: 'Inicio' })).toBeVisible()
}

test.describe('sin sesión', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('credenciales incorrectas muestran un error', async ({ page }) => {
    await page.goto('./')
    await page.getByLabel('Correo').fill(ADMIN_EMAIL)
    await page.getByLabel('Contraseña').fill('incorrecta-123')
    await page.getByRole('button', { name: 'Iniciar sesión' }).click()
    await expect(page.getByRole('alert')).toContainText('incorrectos')
  })
})

test('el dashboard muestra los nodos simulados en vivo', async ({ page }) => {
  await login(page)
  await expect(page.getByText('En vivo')).toBeVisible()
  // Los nodos del simulador se registran solos con su primera lectura
  // La tarjeta es el primer enlace al nodo (también aparece en la lista de ubicaciones)
  const card = page.getByRole('link', { name: /sim-sala/ }).first()
  await expect(card).toBeVisible()
  await expect(card.getByText('En línea')).toBeVisible()
  await expect(card.getByText(/°C/).first()).toBeVisible()
})

test('detalle del nodo: gráficas y relé con confirmación del nodo', async ({ page }) => {
  await login(page)
  await page.getByRole('link', { name: /sim-sala/ }).first().click()
  await expect(page.getByRole('img', { name: 'Gráfica de Temperatura', exact: true }).first()).toBeVisible()

  const relay = page.locator('section', { has: page.getByRole('heading', { name: 'Relé' }) })
  const turnOn = relay.getByRole('button', { name: 'Encender' })
  const turnOff = relay.getByRole('button', { name: 'Apagar' })
  const target = (await turnOn.isEnabled()) ? turnOn : turnOff
  const expected = target === turnOn ? 'Encendido' : 'Apagado'
  await target.click()
  // El simulador responde el ACK por MQTT y el estado llega por WebSocket
  await expect(relay.getByText('Confirmado').first()).toBeVisible()
  await expect(relay.getByText(expected, { exact: true })).toBeVisible()
})

test('reglas: la vista previa ejecuta el motor sobre el histórico', async ({ page }) => {
  await login(page)
  await page.getByRole('link', { name: 'Reglas' }).click()
  await expect(page.getByRole('cell', { name: 'Temperatura alta' })).toBeVisible()
  await page.getByRole('button', { name: 'Nueva regla' }).click()
  await page.getByLabel('Nodo para la vista previa').selectOption({ label: 'sim-sala' })
  await page.getByRole('button', { name: 'Probar regla' }).click()
  await expect(page.getByText(/Esta regla se habría disparado/)).toBeVisible()
})
