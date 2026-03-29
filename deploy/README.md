# AI Agent Deployment Template

This template deploys the project with Docker Compose:
- Nginx as the public entrypoint
- Frontend (Next.js)
- Backend (Spring Boot)
- MySQL and MongoDB (local containers)

## 1. Prerequisites

- Docker 24+
- Docker Compose v2
- Linux server recommended (Ubuntu 22.04+)

## 2. Prepare environment variables

From project root:

1. Copy `.env.example` to `.env`
2. Fill all keys, especially API keys and passwords

## 3. Start services

From project root:

- Build and start:
  - `docker compose up -d --build`

- Check status:
  - `docker compose ps`

- View logs:
  - `docker compose logs -f nginx`
  - `docker compose logs -f backend`
  - `docker compose logs -f frontend`

## 4. Access

- App: `http://<server-ip>`
- API through Nginx path forwarding: `/api/*` -> backend `/*`

## 5. Stop

- `docker compose down`

## 6. Production hardening checklist

- Enable HTTPS with reverse proxy (Nginx + certbot or cloud load balancer)
- Restrict inbound ports to 80/443 only
- Keep database ports private (no public exposure)
- Rotate all credentials and API keys
- Enable backup for MySQL and MongoDB volumes
- Add monitoring and alerting (CPU, memory, disk, error rate)

## 7. If you use managed databases

- Remove or disable `mysql` and `mongodb` services in `docker-compose.yml`
- Set backend env vars to managed endpoints:
  - `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`
  - `MONGODB_URI`

## 8. HTTPS (Let's Encrypt)

- Use an accessible domain that resolves to your server IP.
- Run on server:
  - `cd /opt/ai-agent`
  - `./deploy/scripts/enable_https.sh <your-domain> [email]`
- Verify:
  - `curl -I https://<your-domain>`

Auto-renew is installed at:
- `/usr/local/bin/ai-agent-certbot-renew.sh`
- `/etc/cron.d/ai-agent-certbot`

## 9. Managed DB Mode (no local MySQL/Mongo containers)

- Copy `.env.managed.example` to `.env` and fill managed endpoints.
- Start with:
  - `docker compose -f docker-compose.managed.yml up -d --build`
