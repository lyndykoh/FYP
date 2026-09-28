# Production HTTPS

## Architecture

Docker Compose runs Next.js (3000), three Spring Boot APIs (8081–8083), and
PostgreSQL (5432). `compose.production.yaml` removes their host port mappings.
Only Nginx publishes **80/443**, terminating TLS and proxying private HTTP:

```text
Internet HTTPS :443 → Nginx → frontend / auth / learning / grading
Internet HTTP  :80  → 308 HTTPS redirect (except ACME HTTP-01 challenges)
```

Browser APIs use same-origin `/auth`, `/learning`, and `/grading` prefixes;
Nginx strips those prefixes. Production CORS and `PUBLIC_APP_ORIGIN` are derived
from `PUBLIC_APP_DOMAIN`. The latter keeps Next.js login/role redirects on the
public HTTPS origin even though the upstream connection is HTTP.
Nginx replaces incoming forwarding headers with the actual client IP, validated
host, port 443 and HTTPS scheme, and removes `Forwarded`. It is the direct
Internet edge: do not put another proxy in front without configuring its exact
trusted addresses. Spring's stateless APIs do not require forwarded-header trust.

There are no application WebSockets, OAuth callbacks, Telegram integrations, or
external incoming webhooks. The proxy supports WebSocket upgrades for future
same-origin `wss://` clients. AI provider endpoints already use HTTPS; internal
service URLs and health probes intentionally remain HTTP.

## First deployment

Use Docker Engine with Compose **2.24.4+** (the overlay uses `!reset`). On a Linux
host with systemd, Docker and Make installed:

1. Point the domain's **A** record at the host. Publish **AAAA** only if IPv6
   reaches the same host. Permit inbound TCP **80 and 443** in the host/cloud
   firewall and any NAT; leave them open for redirects and ACME renewal.
   Do not expose 3000, 5432, 8080, or 8081–8083. Allow outbound DNS and HTTPS to
   Let's Encrypt, image registries, and the configured AI provider.
2. Configure the deployment:

   ```bash
   cp .env.production.example .env.production
   make production-secrets
   # Edit .env.production and ../secrets.txt before continuing.
   make production-config
   make production-cert
   make production-up
   make production-ps
   ```

   Set `PUBLIC_APP_DOMAIN` to a hostname you control (no scheme, port or path),
   `ACME_EMAIL` to your certificate-account email, and `TLS_DIRECTORY` to a
   persistent directory **outside the repository** (default `../letsencrypt`).
   Set the AI provider key and bootstrap Tutor credentials in `../secrets.txt`,
   retaining mode 600. Existing files are never overwritten by the secret generator.

3. `production-cert` uses Certbot's standalone HTTP-01 listener, so port **80
   must be free** during initial issuance. On an existing deployment, stop only
   the edge first using the Compose command below with `stop nginx`; then run
   `make production-cert` and `make production-up`. This incurs a brief outage.
   Issuance requires real public DNS; a hosts-file entry is insufficient.
4. Install automatic renewal, after editing `WorkingDirectory` in the service
   file to the absolute repository path on this host:

   ```bash
   sudo install -m 644 docker/systemd/lumina-cert-renew.service /etc/systemd/system/
   sudo install -m 644 docker/systemd/lumina-cert-renew.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now lumina-cert-renew.timer
   make production-renew-test
   systemctl list-timers lumina-cert-renew.timer
   ```

   The timer checks twice daily with up to one hour of jitter and catches missed
   runs after reboot. `make production-renew` uses HTTP-01 webroot validation
   through the running Nginx, then validates and gracefully reloads Nginx.
   Certificate files and the ACME account persist in `TLS_DIRECTORY`; Nginx
   mounts the entire tree read-only so `live/` → `archive/` symlinks and renewed
   files remain visible. No Docker socket is mounted in Certbot. Monitor failures
   with `journalctl -u lumina-cert-renew.service` and external expiry monitoring.
   On non-systemd hosts, schedule `make production-renew` twice daily from the
   checkout using the host scheduler and monitor its exit status.

Production Make targets use:

```bash
docker compose --env-file .env.production --env-file ../secrets.txt \
  -f compose.yaml -f compose.production.yaml <command>
```

Back up the external certificate directory and secrets securely. Never commit
keys or certificates. `production-reset` deletes application data and is only
for disposable environments.

## Existing certificates and private VM tests

Externally managed certificates are also supported: set `TLS_DIRECTORY` to a
certificate directory, and `TLS_CERT_FILE` / `TLS_KEY_FILE` to relative PEM paths
inside it. Defaults are `live/PUBLIC_APP_DOMAIN/fullchain.pem` and
`live/PUBLIC_APP_DOMAIN/privkey.pem`. The chain must match the hostname and key.
After external renewal, validate and reload Nginx using the Compose command
above with `exec -T nginx nginx -t` and then `exec -T nginx nginx -s reload`.
Use the external issuer's renewal scheduler instead of the Certbot timer.

When upgrading the old VM configuration, replace `TLS_CERT_PATH` / `TLS_KEY_PATH`
with `TLS_DIRECTORY=../tls`, `TLS_CERT_FILE=fullchain.pem`, and
`TLS_KEY_FILE=privkey.pem`. `make vm-tls` still generates a 30-day self-signed
certificate for the existing `lumina.sg` VM test. Trust it explicitly on the
private test client; it is **not a public production certificate**. A public
deployment should use the Let's Encrypt workflow above instead.

## Security and local development

`compose.yaml`, `.env.example`, and `npm run dev` retain local HTTP without
certificates. Production builds use relative API URLs, avoiding mixed content.
The existing browser auth cookie uses `Secure` on HTTPS and `SameSite=Lax`;
JWT bearer authentication remains unchanged. Tokens are still accessible to
JavaScript/localStorage, an existing limitation documented in the README.

HSTS defaults off. After trusted HTTPS and renewal have been verified, set
`SECURITY_HEADERS_HSTS_ENABLED=true` and run `make production-up`. Nginx sends
`max-age=31536000` for this host only, with no subdomain policy or preload.
Keep it off for private VM tests. The edge hides upstream HSTS policies.

## Verification on the deployment host

Replace `your-domain.example` below with the configured hostname. Never bypass
certificate verification with `-k`:

```bash
curl -sS -D - -o /dev/null 'http://your-domain.example/login?next=%2Fclasses'
# 308; Location: https://your-domain.example/login?next=%2Fclasses
curl -fsS https://your-domain.example/healthz
curl -fsS https://your-domain.example/auth/actuator/health
curl -fsS https://your-domain.example/learning/actuator/health
curl -fsS https://your-domain.example/grading/actuator/health
curl -sS -D - -o /dev/null https://your-domain.example/
# Login redirect stays on the public HTTPS hostname, with no internal port.
make production-ps
make production-renew-test
```

Log in as each role, navigate, upload and log out in a browser. Check that all
API requests use HTTPS, the console has no mixed-content errors, and the auth
cookie has Secure/SameSite=Lax. Confirm backend/database ports are unreachable
from outside the host. The local Nginx health probe uses `127.0.0.1/healthz`;
public HTTP `/healthz` redirects like every non-ACME path.

References: [Certbot Docker installation](https://eff-certbot.readthedocs.io/en/stable/install.html#running-with-docker),
[Certbot renewal](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates),
[Nginx proxy headers](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header).
