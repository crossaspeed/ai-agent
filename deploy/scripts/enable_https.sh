#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <domain> [email]"
  exit 1
fi

DOMAIN="$1"
EMAIL="${2:-}"
PROJECT_DIR="/opt/ai-agent"

cd "$PROJECT_DIR"
mkdir -p deploy/certbot/www

cp deploy/nginx/http-only.conf deploy/nginx/default.conf
docker compose up -d nginx

CERTBOT_ARGS=(certonly --webroot -w /var/www/certbot -d "$DOMAIN" --agree-tos --non-interactive)
if [ -n "$EMAIL" ]; then
  CERTBOT_ARGS+=(--email "$EMAIL")
else
  CERTBOT_ARGS+=(--register-unsafely-without-email)
fi

docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v "$PROJECT_DIR"/deploy/certbot/www:/var/www/certbot \
  certbot/certbot:latest \
  "${CERTBOT_ARGS[@]}"

sed "s/__DOMAIN__/$DOMAIN/g" deploy/nginx/https.conf.template > deploy/nginx/default.conf

docker compose up -d nginx
docker compose exec -T nginx nginx -t
docker compose exec -T nginx nginx -s reload

cat >/usr/local/bin/ai-agent-certbot-renew.sh <<'RENEW'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/ai-agent
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /opt/ai-agent/deploy/certbot/www:/var/www/certbot \
  certbot/certbot:latest \
  renew --webroot -w /var/www/certbot --quiet

docker compose exec -T nginx nginx -s reload
RENEW
chmod +x /usr/local/bin/ai-agent-certbot-renew.sh

cat >/etc/cron.d/ai-agent-certbot <<'CRON'
21 3 * * * root /usr/local/bin/ai-agent-certbot-renew.sh >> /var/log/ai-agent-certbot-renew.log 2>&1
CRON

echo "HTTPS enabled for $DOMAIN and auto-renew is scheduled."
