# DispatchFlow Frontend

Vue 3 + TypeScript admin console and monitoring UI for the DispatchFlow dispatch platform.

## Pages

| Route | Description |
|-------|-------------|
| `/dashboard` | Dispatch overview and KPIs |
| `/orders` | Order list and detail |
| `/tasks` | Dispatch task list and detail |
| `/vehicles` | Vehicle list and detail |
| `/vehicle-tracking` | Full-screen map monitor |
| `/exceptions` | Exception management |
| `/mobile/order` | Mobile-friendly park order form |

## Development

```bash
npm install
npm run dev
```

The dev server runs on port **3000** and proxies `/api` to `http://localhost:8080`.

## Build

```bash
npm run build
npm run preview   # preview production build
npm run typecheck # TypeScript check only
```

## Environment

Optional `.env.local`:

```env
# Vite proxy target (default: http://localhost:8080)
# VITE_API_PROXY=http://localhost:8080
```

## Stack

- Vue 3, Vue Router, Pinia
- Ant Design Vue
- Leaflet (vehicle tracking map)
- Axios
