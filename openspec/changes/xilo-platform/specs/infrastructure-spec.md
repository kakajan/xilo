# Spec: Infrastructure & DevOps

## Overview
Fully containerized deployment with Docker and Kubernetes. Monitoring with Prometheus + Grafana + Loki. Self-hosted error tracking with Sentry. CI/CD pipeline for automated testing and deployment.

---

## Requirements

### REQ-INF-001: Containerization

**All services** are Dockerized with multi-stage builds:
- Go backend: scratch-based minimal image (< 20MB)
- Next.js frontend: standalone output image
- Flutter: not containerized (build via CI, distribute via stores)

**Docker Compose** for local development spins up:
```yaml
services:
  postgres, redis, nats, meilisearch, minio,
  auth-service, user-service, post-service,
  comment-service, notification-service,
  search-service, media-service,
  api-gateway, web-frontend,
  prometheus, grafana, loki, sentry
```

### REQ-INF-002: Kubernetes Deployment

**Production** runs on Kubernetes with:
- **Deployments** for stateless services (3 replicas minimum)
- **StatefulSets** for PostgreSQL, Redis, NATS, MinIO
- **HPA** (Horizontal Pod Autoscaler) on CPU/memory metrics
- **Ingress** via Traefik with Let's Encrypt TLS
- **ConfigMaps** and **Secrets** for configuration
- **PersistentVolumeClaims** for stateful data
- **NetworkPolicy** for inter-service isolation
- **PodDisruptionBudget** for availability during maintenance

### REQ-INF-003: CI/CD Pipeline

**GitHub Actions** or self-hosted runner:

```
Push to main → Build → Test → Lint → Build Images → Push to Registry → Deploy to Staging
Tag release → Build → Test → Build Images → Deploy to Production (canary → full)
```

**Pipeline steps:**
1. Checkout + Setup Go/Node/Flutter
2. Run unit tests (all services)
3. Run integration tests
4. Lint (golangci-lint, ESLint, dart analyze)
5. Build Docker images
6. Push to container registry
7. Deploy to Kubernetes (kubectl apply / Helm)
8. Smoke tests
9. Rollback on failure

### REQ-INF-004: Monitoring & Observability

| Tool | Purpose | Data |
|------|---------|------|
| **Prometheus** | Metrics collection | HTTP latency, error rates, Go runtime, DB pool |
| **Grafana** | Dashboards + alerting | Service dashboards, business KPIs |
| **Loki** | Log aggregation | Structured JSON logs from all services |
| **Sentry** (self-hosted) | Error tracking | Stack traces, breadcrumbs, release tracking |

**Key metrics:**
- `http_request_duration_seconds` (histogram)
- `http_requests_total` (counter)
- `active_websocket_connections` (gauge)
- `postgres_connections_active` (gauge)
- `redis_commands_total` (counter)
- `nats_messages_published_total` (counter)

### REQ-INF-005: Reverse Proxy (Traefik)

- Auto TLS via Let's Encrypt
- Path-based routing to API Gateway: `/api/*`
- Path-based routing to Web Frontend: `/*`
- WebSocket upgrade support
- Rate limiting middleware
- CORS headers
- Gzip/Brotli compression
- IP whitelist for admin endpoints (optional)

### REQ-INF-006: Backup & Disaster Recovery

- PostgreSQL: WAL archiving + daily pg_dump to MinIO
- Redis: RDB snapshots every hour
- MinIO: mirror to secondary bucket (cross-region optional)
- Retention: daily backups kept 30 days, weekly kept 90 days

### REQ-INF-007: Security Hardening

- All inter-service communication over HTTPS/TLS
- Secrets managed via Kubernetes Secrets (never in code/config)
- Network policies restricting pod-to-pod traffic
- Non-root containers (runAsUser: 1000)
- Read-only root filesystem where possible
- Image vulnerability scanning (Trivy in CI)
- Regular dependency updates (Dependabot/Renovate)

---

## Service Resource Limits

| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---------|------------|-----------|----------------|--------------|
| PostgreSQL | 1 | 4 | 2Gi | 8Gi |
| Redis | 0.5 | 2 | 512Mi | 2Gi |
| NATS | 0.25 | 1 | 256Mi | 1Gi |
| Meilisearch | 0.5 | 2 | 512Mi | 2Gi |
| MinIO | 0.5 | 2 | 512Mi | 2Gi |
| API Gateway | 0.25 | 1 | 128Mi | 512Mi |
| Auth Service | 0.25 | 1 | 128Mi | 512Mi |
| Other Services | 0.25 | 1 | 128Mi | 512Mi |
| Web Frontend | 0.5 | 2 | 256Mi | 1Gi |
| Prometheus | 0.5 | 2 | 1Gi | 4Gi |
| Grafana | 0.25 | 1 | 256Mi | 512Mi |
| Loki | 0.5 | 2 | 512Mi | 2Gi |
