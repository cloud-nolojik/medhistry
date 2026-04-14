#!/bin/bash
# =============================================================================
# MedHistry — Local Deploy Script (runs from your Mac)
# =============================================================================
# Usage:
#   ./deploy.sh setup     — First time: push code + SSL + full deploy
#   ./deploy.sh deploy    — Push code + rebuild + restart
#   ./deploy.sh status    — Check service health
#   ./deploy.sh logs      — Tail API logs
#   ./deploy.sh ssh       — Open SSH session to server
#   ./deploy.sh renew     — Manually renew SSL cert
# =============================================================================

set -e

# --- Config ---
SERVER="root@168.144.23.210"
SERVER_DIR="/opt/medhistry"
DOMAIN="medhistry.com"
EMAIL="vijesh.krishna@nolojik.com"
COMPOSE="docker compose -f docker-compose.prod.yml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[!!]${NC} $1"; }
err()  { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

# ---------------------------------------------------------------------------
# Run command on server via SSH
# ---------------------------------------------------------------------------
remote() {
    ssh -o StrictHostKeyChecking=no "$SERVER" "cd $SERVER_DIR && $1"
}

# ---------------------------------------------------------------------------
# Push local code to server
# ---------------------------------------------------------------------------
push_code() {
    # Auto-commit any changes (skip .gradle, .DS_Store, release builds)
    if ! git diff --quiet HEAD -- . ':!mobile/.gradle' ':!mobile/.idea' ':!.DS_Store' ':!mobile/*/release' 2>/dev/null || \
       [ -n "$(git ls-files --others --exclude-standard -- . ':!mobile/.gradle' ':!mobile/.idea' ':!.DS_Store' ':!mobile/*/release' 2>/dev/null)" ]; then
        log "Staging changes..."
        git add -A -- . ':!mobile/.gradle' ':!mobile/.idea' ':!.DS_Store' ':!mobile/*/release'
        log "Committing..."
        git commit -m "deploy: $(date '+%Y-%m-%d %H:%M')" || warn "Nothing to commit"
    else
        log "No changes to commit"
    fi

    log "Pushing code to GitHub..."
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    git push origin "${BRANCH}:main" || err "Git push failed"

    log "Pulling code on server..."
    remote "git stash -q 2>/dev/null; git pull origin main; git stash drop -q 2>/dev/null"
    log "Server code updated"
}

# ---------------------------------------------------------------------------
# SETUP: First-time deployment (push + SSL + everything)
# ---------------------------------------------------------------------------
cmd_setup() {
    echo ""
    echo "========================================="
    echo "  MedHistry — First Time Setup"
    echo "========================================="
    echo ""

    # Push latest code
    push_code

    # Step 1: Start with HTTP-only nginx
    log "Starting services with HTTP-only config..."
    remote "cp nginx/conf.d/default.conf.initial nginx/conf.d/default.conf"
    remote "$COMPOSE up -d --build db api nginx"
    log "Waiting for services to boot..."
    sleep 10

    # Step 2: Health check
    log "Checking API health..."
    remote "curl -sf http://localhost/health" && log "API is healthy" || warn "API not responding yet — continuing"

    # Step 3: Init database
    log "Initializing database..."
    remote "$COMPOSE exec -T api python -c \"
from app.core.database import engine, Base
import asyncio
async def init():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print('Tables created/verified')
asyncio.run(init())
\"" || warn "DB init failed — tables may already exist"

    # Step 4: SSL certificate
    log "Requesting SSL certificate..."
    remote "$COMPOSE run --rm certbot certonly \
        --webroot \
        --webroot-path=/var/www/certbot \
        --email $EMAIL \
        --agree-tos \
        --no-eff-email \
        -d $DOMAIN \
        -d www.$DOMAIN \
        -d app.$DOMAIN"

    if [ $? -ne 0 ]; then
        warn "SSL failed — DNS may not be pointing to server yet"
        warn "API is running on HTTP at http://$DOMAIN"
        exit 1
    fi

    # Step 5: Switch to HTTPS nginx config
    log "Switching to HTTPS config..."
    remote "git checkout -- nginx/conf.d/default.conf && $COMPOSE restart nginx"
    log "HTTPS is live!"

    # Step 6: Auto-renewal cron
    remote "crontab -l 2>/dev/null | grep -q 'certbot renew' || (crontab -l 2>/dev/null; echo '0 3 * * * cd $SERVER_DIR && $COMPOSE run --rm certbot renew --quiet && $COMPOSE restart nginx') | crontab -"
    log "Cert auto-renewal cron set"

    echo ""
    echo "========================================="
    echo "  Setup Complete!"
    echo "========================================="
    echo "  API:    https://$DOMAIN/api/v1"
    echo "  Health: https://$DOMAIN/health"
    echo ""
    echo "  Future deploys: ./deploy.sh deploy"
    echo "========================================="
}

# ---------------------------------------------------------------------------
# DEPLOY: Push code + rebuild + restart
# ---------------------------------------------------------------------------
cmd_deploy() {
    echo ""
    echo "========================================="
    echo "  MedHistry — Deploy Update"
    echo "========================================="
    echo ""

    # Push latest code
    push_code

    # Ensure DB viewer credentials exist on server (auto-generate if missing)
    log "Checking DB viewer credentials..."
    remote "grep -q DB_VIEWER_USER .env 2>/dev/null || {
        echo 'DB_VIEWER_USER=medadmin' >> .env;
        echo \"DB_VIEWER_PASS=\$(openssl rand -hex 16)\" >> .env;
        echo '[OK] Generated DB_VIEWER_PASS in /opt/medhistry/.env';
    }"

    # Rebuild API — force a clean build so new code actually ships.
    # Without --no-cache, Docker can reuse a cached `COPY . .` layer when
    # mtimes look similar, and old bytecode/venv layers get baked in.
    log "Rebuilding API container (no cache)..."
    remote "$COMPOSE build --no-cache --pull api"

    # Restart — force-recreate so the running container is replaced even if
    # the image digest hash looks unchanged to compose.
    log "Restarting services (force recreate)..."
    remote "$COMPOSE up -d --force-recreate --no-deps api && $COMPOSE restart nginx"
    sleep 5

    # Prune dangling images from previous builds so disk doesn't balloon
    remote "docker image prune -f >/dev/null 2>&1 || true"

    # Init DB (in case new models)
    log "Checking database..."
    remote "$COMPOSE exec -T api python -c \"
from app.core.database import engine, Base
import asyncio
async def init():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print('Tables created/verified')
asyncio.run(init())
\"" || warn "DB init skipped"

    # Health check
    log "Health check..."
    sleep 3
    remote "curl -sf http://localhost/health" && log "API is healthy!" || warn "Health check failed — run: ./deploy.sh logs"

    echo ""
    log "Deploy complete!"
    cmd_status
}

# ---------------------------------------------------------------------------
# STATUS
# ---------------------------------------------------------------------------
cmd_status() {
    echo ""
    echo "--- Service Status ---"
    remote "$COMPOSE ps"
    echo ""
    echo "--- API Health ---"
    remote "curl -s http://localhost/health" || echo "Not responding"
    echo ""
}

# ---------------------------------------------------------------------------
# LOGS
# ---------------------------------------------------------------------------
cmd_logs() {
    ssh -o StrictHostKeyChecking=no "$SERVER" "cd $SERVER_DIR && $COMPOSE logs -f --tail=100 api"
}

# ---------------------------------------------------------------------------
# SSH: Open interactive session
# ---------------------------------------------------------------------------
cmd_ssh() {
    ssh -o StrictHostKeyChecking=no "$SERVER" -t "cd $SERVER_DIR && bash"
}

# ---------------------------------------------------------------------------
# RENEW
# ---------------------------------------------------------------------------
cmd_renew() {
    log "Renewing SSL certificate..."
    remote "$COMPOSE run --rm certbot renew && $COMPOSE restart nginx"
    log "Done"
}

# ---------------------------------------------------------------------------
# DB VIEWER — run admin queries against the production database
# ---------------------------------------------------------------------------
# Usage:
#   ./deploy.sh db list                — list all patients
#   ./deploy.sh db show <name>         — show full record for patient(s) matching name
#   ./deploy.sh db export <name>       — dump matching patient(s) to JSON + scp to ./
#   ./deploy.sh db doctors             — list all doctors
#   ./deploy.sh db counts              — row counts per table
cmd_db() {
    local sub="${1:-list}"; shift || true
    local arg="$*"
    log "Running db_viewer.py $sub $arg ..."
    # Run inside the API container so it has DATABASE_URL from env
    remote "$COMPOSE exec -T api python -m scripts.db_viewer $sub $arg"

    # If this was an export, pull the JSON back to the Mac
    if [ "$sub" = "export" ] && [ -n "$arg" ]; then
        log "Fetching exported JSON to local ./ ..."
        # Copy the most recent export file
        ssh -o StrictHostKeyChecking=no "$SERVER" \
            "docker compose -f $SERVER_DIR/docker-compose.prod.yml exec -T api sh -c 'ls -t /tmp/patient_export_*.json 2>/dev/null | head -1'" \
            | while read -r REMOTE_FILE; do
                [ -z "$REMOTE_FILE" ] && continue
                LOCAL_NAME="$(basename "$REMOTE_FILE")"
                ssh -o StrictHostKeyChecking=no "$SERVER" \
                    "docker compose -f $SERVER_DIR/docker-compose.prod.yml exec -T api cat '$REMOTE_FILE'" \
                    > "./$LOCAL_NAME"
                log "Saved to ./$LOCAL_NAME"
            done
    fi
}

# ---------------------------------------------------------------------------
# DB-WEB — print web DB viewer URL + auth credentials
# ---------------------------------------------------------------------------
cmd_db_web() {
    echo ""
    echo "======================================"
    echo "  Web DB Viewer (pgweb)"
    echo "======================================"
    local creds
    creds=$(ssh -o StrictHostKeyChecking=no "$SERVER" "grep -E 'DB_VIEWER_(USER|PASS)' $SERVER_DIR/.env 2>/dev/null")
    if [ -z "$creds" ]; then
        warn "No DB viewer credentials yet on server."
        warn "Run ./deploy.sh deploy once — it auto-generates them."
        return
    fi
    echo ""
    echo "URL:      https://$DOMAIN/db/"
    echo ""
    echo "$creds" | sed 's/^/  /'
    echo ""
    echo "Opens a Postgres browser with read-only access to the medhistry DB."
    echo "Browse tables, run SELECT queries, export rows to CSV/JSON."
    echo ""
}

# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------
case "${1:-help}" in
    setup)  cmd_setup ;;
    deploy) cmd_deploy ;;
    status) cmd_status ;;
    logs)   cmd_logs ;;
    ssh)    cmd_ssh ;;
    renew)  cmd_renew ;;
    db)     shift; cmd_db "$@" ;;
    db-web) cmd_db_web ;;
    *)
        echo "MedHistry Deploy (runs from your Mac)"
        echo ""
        echo "Usage: ./deploy.sh <command>"
        echo ""
        echo "Commands:"
        echo "  setup          — First-time: push + SSL + full deploy"
        echo "  deploy         — Push code + rebuild + restart"
        echo "  status         — Check service health"
        echo "  logs           — Tail API logs"
        echo "  ssh            — Open SSH session to server"
        echo "  renew          — Renew SSL certificate"
        echo "  db list        — List all patients in prod DB"
        echo "  db show <name> — Show records for patient matching name"
        echo "  db export <name> — Export patient to JSON + download"
        echo "  db doctors     — List all doctors"
        echo "  db counts      — Row counts per table"
        echo "  db-web         — Print web DB viewer URL + credentials"
        ;;
esac
