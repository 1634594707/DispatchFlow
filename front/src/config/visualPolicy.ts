/**
 * Visual-only redesign contract.
 * This policy is intentionally independent from routes, permissions and data.
 */

export const visualPolicy = {
  id: 'operational-clarity',
  label: 'Operational Clarity',
  controls: {
    designVariance: 3,
    motionIntensity: 2,
    visualDensity: 8,
  },
  accentUsage: ['selected', 'action', 'realtime'] as const,
  semanticUsage: {
    error: ['risk', 'failure', 'offline'],
    warning: ['attention', 'degraded', 'pending'],
    success: ['healthy', 'completed', 'online'],
  },
  containerUsage: ['independent-reading', 'draggable', 'overlay'] as const,
  prohibitedDecoration: [
    'grid-background',
    'ambient-glow',
    'generic-gradient',
    'decorative-shimmer',
    'infinite-pulse',
    'hover-lift',
  ] as const,
  protectedBoundaries: [
    'url',
    'route-name',
    'api-contract',
    'permission',
    'business-flow',
    'analytics-event',
    'map-business-rule',
  ] as const,
} as const

export function applyVisualPolicy(root: HTMLElement): void {
  root.dataset.visualPolicy = visualPolicy.id
  root.dataset.designVariance = String(visualPolicy.controls.designVariance)
  root.dataset.motionIntensity = String(visualPolicy.controls.motionIntensity)
  root.dataset.visualDensity = String(visualPolicy.controls.visualDensity)
}
