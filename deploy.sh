#!/bin/bash
# MedHistry Production Deployment Script
# Usage: ./deploy.sh
# Runs on the PRODUCTION SERVER after git clone/pull

set -e

DOMAIN="medhistry.com"
EMAIL="vijesh.krishna@nolojik.com"

echo "=== MedHistry Deployment ==="

# Step 1: Start with HTTP-only nginx (no SSL cert yet)
echo "[1/5] Starting services with HTTP-only config..."
cp nginx/conf.d/default.conf.initial nginx/conf.d/default.conf.active
# Use the initial config for first boot
cp nginx/conf.d/default.conf.initial nginx/conf.d/default.conf

docker compose -f docker-compose.prod.yml up -d --build db api nginx
echo "Waiting for services to start..."
sleep 10

# Step 2: Run database migrations
echo "[2/5] Running database migrations..."
docker compose -f docker-compose.prod.yml exec api python -c "
from app.db import engine
from app.models import Base
import asyncio
async def init():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print('Tables created/verified')
asyncio.run(init())
"

# Step 3: Obtain SSL certificate
echo "[3/5] Obtaining SSL certificate for $DOMAIN..."
docker compose -f docker-compose.prod.yml run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN" \
    -d "www.$DOMAIN"

# Step 4: Switch to HTTPS nginx config
echo "[4/5] Switching to HTTPS config..."
cp nginx/conf.d/default.conf.initial nginx/conf.d/default.conf.http-backup
# Restore the full SSL config
git checkout nginx/conf.d/default.conf
docker compose -f docker-compose.prod.yml restart nginx

# Step 5: Set up auto-renewal cron
echo "[5/5] Setting up certificate auto-renewal..."
(crontab -l 2>/dev/null; echo "0 3 * * * cd $(pwd) && docker compose -f docker-compose.prod.yml run --rm certbot renew && docker compose -f docker-compose.prod.yml restart nginx") | crontab -

echo ""
echo "=== Deployment Complete ==="
echo "API: https://$DOMAIN/api/v1"
echo "Health: https://$DOMAIN/health"
echo ""
echo "To check status: docker compose -f docker-compose.prod.yml ps"
echo "To view logs: docker compose -f docker-compose.prod.yml logs -f api"
