---
name: xilo-infra
description: Use when working on Docker, Kubernetes, CI/CD, monitoring, or deployment for the Xilo platform. Covers container patterns, Helm charts, Prometheus/Grafana/Loki setup, and security hardening.
---

# Xilo Infrastructure Development

Docker, Kubernetes, Traefik, Prometheus, Grafana, Loki, Sentry (self-hosted).

## Directory Structure

```
infra/
├── docker/
│   ├── Dockerfile.api-gateway
│   ├── Dockerfile.auth-service
│   ├── Dockerfile.web
│   └── ...
├── docker-compose.yml           # Development
├── docker-compose.prod.yml      # Production (single-node)
├── kubernetes/
│   ├── helm/xilo/
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   │       ├── api-gateway/
│   │       ├── auth-service/
│   │       ├── postgres/
│   │       ├── redis/
│   │       ├── nats/
│   │       ├── traefik/
│   │       └── monitoring/
│   └── manifests/               # Raw K8s manifests (optional)
└── terraform/                   # IaC (optional)
```

## Docker Patterns

### Go Service Dockerfile

```dockerfile
# Stage 1: Build
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/server ./cmd/auth-service

# Stage 2: Minimal runtime
FROM scratch
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
COPY --from=builder /app/server /server
USER 1000:1000
EXPOSE 8010
ENTRYPOINT ["/server"]
```

### Next.js Web Dockerfile

```dockerfile
FROM node:20-alpine AS base
FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --only=production

FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -g 1001 -S nodejs && adduser -S nextjs -u 1001
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
```

## Docker Compose (Development)

```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: xilo
      POSTGRES_PASSWORD: xilo_dev
      POSTGRES_DB: xilo
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U xilo"]
      interval: 5s

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    volumes: [redisdata:/data]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s

  nats:
    image: nats:2-alpine
    ports: ["4222:4222", "8222:8222"]
    command: "-js"

  meilisearch:
    image: getmeili/meilisearch:v1.10
    ports: ["7700:7700"]
    environment:
      MEILI_MASTER_KEY: dev_master_key
    volumes: [meilidata:/meili_data]

  minio:
    image: minio/minio:latest
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address :9001
    volumes: [miniodata:/data]

volumes:
  pgdata: redisdata: meilidata: miniodata:
```

## Kubernetes Helm Chart

```yaml
# infra/kubernetes/helm/xilo/values.yaml
replicaCount: 3

image:
  registry: ghcr.io/xilo

postgres:
  host: postgres.xilo.svc.cluster.local
  port: 5432
  database: xilo

redis:
  host: redis.xilo.svc.cluster.local
  port: 6379

nats:
  url: nats://nats.xilo.svc.cluster.local:4222

resources:
  requests:
    cpu: 250m
    memory: 128Mi
  limits:
    cpu: 1000m
    memory: 512Mi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 80

monitoring:
  enabled: true
  serviceMonitor: true
```

```yaml
# Example Deployment template
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.name }}
  labels: {{ include "xilo.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels: {{ include "xilo.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels: {{ include "xilo.selectorLabels" . | nindent 8 }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "{{ .Values.service.port }}"
    spec:
      securityContext:
        runAsUser: 1000
        runAsNonRoot: true
        readOnlyRootFilesystem: true
      containers:
        - name: {{ .Values.name }}
          image: "{{ .Values.image.registry }}/{{ .Values.name }}:{{ .Values.image.tag }}"
          ports:
            - containerPort: {{ .Values.service.port }}
          resources: {{ toYaml .Values.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /health
              port: {{ .Values.service.port }}
          readinessProbe:
            httpGet:
              path: /ready
              port: {{ .Values.service.port }}
          envFrom:
            - secretRef:
                name: {{ .Values.name }}-secret
```

## Monitoring Setup

### Prometheus Annotations

Services expose metrics at `/metrics`. Added via annotation:
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8010"
  prometheus.io/path: "/metrics"
```

### Key Metrics in Go

```go
import "github.com/prometheus/client_golang/prometheus"

var (
    httpRequestsTotal = prometheus.NewCounterVec(
        prometheus.CounterOpts{Name: "http_requests_total"},
        []string{"method", "path", "status"},
    )
    httpRequestDuration = prometheus.NewHistogramVec(
        prometheus.HistogramOpts{
            Name:    "http_request_duration_seconds",
            Buckets: prometheus.DefBuckets,
        },
        []string{"method", "path"},
    )
)
```

### Grafana Dashboard

Import dashboards from `infra/kubernetes/helm/xilo/templates/monitoring/dashboards/`.

### Loki Logging

Go services output JSON to stdout:
```go
import "log/slog"

logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
logger.Info("request completed",
    "method", c.Method(),
    "path", c.Path(),
    "status", c.Response().StatusCode(),
    "duration", duration,
)
```

## CI/CD (GitHub Actions)

```yaml
# .github/workflows/backend-ci.yml
name: Backend CI
on:
  push:
    paths: ['backend/**']
  pull_request:
    paths: ['backend/**']

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: '1.22' }
      - run: go test ./...
        working-directory: backend
      - run: golangci-lint run
        working-directory: backend
      - name: Build Docker image
        run: docker build -t xilo-auth -f infra/docker/Dockerfile.auth-service backend
```

## Security Hardening Checklist

- [ ] All containers run as non-root (`runAsUser: 1000`)
- [ ] Read-only root filesystem where possible
- [ ] Kubernetes NetworkPolicy restricts inter-pod traffic
- [ ] Secrets in Kubernetes Secrets, never in config files
- [ ] Image vulnerability scanning in CI (Trivy)
- [ ] Traefik with Let's Encrypt auto-TLS
- [ ] Rate limiting configured at gateway level
- [ ] CSP, HSTS, X-Frame-Options headers set
- [ ] CORS restricted to known origins
