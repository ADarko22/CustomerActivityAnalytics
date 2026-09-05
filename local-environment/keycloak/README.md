# Keycloak — operator login (Phase 5)

`docker compose up keycloak` starts Keycloak in dev mode (`start-dev`) and imports `realm-export.json`
(`--import-realm`) on every startup, so a fresh clone gets a fully working identity provider with no
manual admin-console setup ([DECISIONS.md](../../docs/DECISIONS.md) D2). Keycloak's admin console is reachable at
`http://localhost:${KEYCLOAK_PORT:-8081}` (login `admin`/`admin`, from the `KEYCLOAK_ADMIN`/
`KEYCLOAK_ADMIN_PASSWORD` env vars set in [docker-compose.yml](../docker-compose.yml)); the app itself talks to the
realm at http://localhost:8081/realms/customer-activity-analytics.

## What the realm export contains

- Realm `customer-activity-analytics`, with two realm roles: `OPERATOR` (read access) and `ADMIN`
  (risk-rule administration — the "editor" role from [PHASE_5.md](../../docs/development/PHASE_5.md)'s Assumptions).
- A single public client, `caa-frontend` — Authorization Code + PKCE (`S256`) only, no client secret,
  no direct-access-grants/password flow. Redirect URIs and web origins are pinned to
  http://localhost:4200 (the Angular dev server).
- The `roles` client scope's realm-role protocol mapper is explicitly configured to add
  `realm_access.roles` to **both** the ID token and the access token — Keycloak's own default only
  guarantees the access token, but the frontend's admin-only Administration section
  ([DECISIONS.md](../../docs/DECISIONS.md) D21) reads the role claim from the ID token.
- Two demo users:

  | Username   | Password   | Realm roles       |
            |------------|------------|-------------------|
  | `operator` | `password` | `OPERATOR`        |
  | `admin`    | `admin`    | `ADMIN`, `OPERATOR` |

  `admin` also holds `OPERATOR` so the admin user can read everything an operator can, in addition to
  managing risk rules — "editor" is additive, not a replacement for read access.

## Re-exporting after a manual change

If you change the realm by hand in the admin console during development (e.g. to try a different
client setting), re-export it so the change survives a fresh `docker compose up`:

```
docker exec caa-keycloak /opt/keycloak/bin/kc.sh export \
  --realm customer-activity-analytics \
  --file /tmp/realm-export.json \
  --users realm_file
docker cp caa-keycloak:/tmp/realm-export.json ./realm-export.json
```

Review the diff before committing — a full export includes generated IDs and internal fields that
aren't worth carrying over; keep only the meaningful change.
