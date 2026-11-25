# ADR 0006: Frontend: React + TypeScript + Vite

Estado: aceptada · Fecha: 2026-11-24

## Decisión
React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query (caché y reintentos), `@stomp/stompjs` y
Apache ECharts (maneja miles de puntos con muestreo LTTB). `HashRouter` para que el mismo build funcione en
nginx y en GitHub Pages.

## Consecuencias
- Los eventos del WebSocket actualizan la caché de TanStack Query en lugar de mantener otro estado aparte.
- La lógica de series (huecos, tramos recuperados del buffer, inserción ordenada) es pura y tiene pruebas.
- ECharts pesa ≈ 200 kB comprimido; se importa por módulos (`echarts/core`).
