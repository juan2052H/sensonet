import { LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  MarkAreaComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { useEffect, useRef } from 'react'
import type { BuiltSeries } from '../lib/series'
import { usePreferences } from '../lib/preferences'

echarts.use([LineChart, GridComponent, TooltipComponent, DataZoomComponent, MarkAreaComponent, MarkLineComponent, CanvasRenderer])

interface Props {
  series: BuiltSeries
  color: string
  unit: string
  title: string
  digits?: number
  /** Umbrales a dibujar como líneas horizontales (p. ej. reglas activas). */
  thresholds?: { value: number; label: string }[]
  height?: number
  compact?: boolean
}

/**
 * Gráfica de una métrica. Los tramos sin datos se ven como cortes en la línea y se sombrean en
 * gris; los tramos rellenados desde el buffer offline se sombrean en violeta: el comportamiento
 * offline-first se ve, no solo se explica.
 */
export function Chart({ series, color, unit, title, digits = 1, thresholds = [], height = 240, compact = false }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const chart = useRef<echarts.ECharts | null>(null)
  const { theme } = usePreferences()
  const dark = theme === 'dark'

  useEffect(() => {
    if (!ref.current) return
    chart.current = echarts.init(ref.current, undefined, { renderer: 'canvas' })
    const ro = new ResizeObserver(() => chart.current?.resize())
    ro.observe(ref.current)
    return () => {
      ro.disconnect()
      chart.current?.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    const text = dark ? '#94a3b8' : '#64748b'
    const grid = dark ? '#1e293b' : '#e2e8f0'
    chart.current?.setOption(
      {
        animation: false,
        grid: compact ? { left: 2, right: 2, top: 4, bottom: 2 } : { left: 48, right: 16, top: 16, bottom: 28 },
        tooltip: compact
          ? { show: false }
          : {
              trigger: 'axis',
              valueFormatter: (v: unknown) => (typeof v === 'number' ? `${v.toFixed(digits)} ${unit}` : '—'),
            },
        xAxis: { type: 'time', show: !compact, axisLabel: { color: text }, axisLine: { lineStyle: { color: grid } } },
        yAxis: {
          type: 'value',
          show: !compact,
          scale: true,
          name: compact ? undefined : unit,
          nameTextStyle: { color: text },
          axisLabel: { color: text },
          splitLine: { lineStyle: { color: grid } },
        },
        series: [
          {
            name: title,
            type: 'line',
            data: series.data,
            showSymbol: false,
            connectNulls: false,
            sampling: 'lttb',
            lineStyle: { width: compact ? 1.5 : 2, color },
            itemStyle: { color },
            areaStyle: compact ? { color, opacity: 0.08 } : undefined,
            markArea: compact
              ? undefined
              : {
                  silent: false,
                  data: [
                    ...series.gaps.map(([a, b]) => [
                      { xAxis: a, name: 'Sin datos', itemStyle: { color: dark ? 'rgba(148,163,184,0.12)' : 'rgba(100,116,139,0.10)' } },
                      { xAxis: b },
                    ]),
                    ...series.backfilled.map(([a, b]) => [
                      { xAxis: a, name: 'Recuperado del buffer', itemStyle: { color: 'rgba(139,92,246,0.14)' } },
                      { xAxis: b },
                    ]),
                  ],
                  label: { color: text, fontSize: 10, position: 'insideTop' },
                },
            markLine:
              compact || thresholds.length === 0
                ? undefined
                : {
                    silent: true,
                    symbol: 'none',
                    lineStyle: { type: 'dashed', color: '#ef4444', opacity: 0.6 },
                    label: { formatter: '{b}', color: '#ef4444', fontSize: 10 },
                    data: thresholds.map((t) => ({ yAxis: t.value, name: t.label })),
                  },
          },
        ],
      },
      { notMerge: true },
    )
  }, [series, color, unit, title, digits, thresholds, dark, compact])

  return <div ref={ref} style={{ height }} role="img" aria-label={`Gráfica de ${title}`} />
}
