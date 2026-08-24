FROM node:20-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

WORKDIR /usr/share/nginx/html
COPY --from=builder /build/dist ./
# The deploy-time config is intentionally not committed. Always provide an
# empty fallback so index.html never requests a missing runtime-config.js.
RUN test -f runtime-config.js || printf '%s\n' 'window.__DISPATCHFLOW_RUNTIME_CONFIG__ = window.__DISPATCHFLOW_RUNTIME_CONFIG__ || {};' > runtime-config.js
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 80: HTTP（位于宿主机 Nginx 反向代理之后）
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]