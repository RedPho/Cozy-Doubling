# Oracle Cloud deploy (one VM)

One Always Free Ampere VM (Ubuntu, `VM.Standard.A1.Flex`, 1 OCPU / 6 GB) in `eu-amsterdam-1` runs everything with Docker Compose:

| Service | What |
|---|---|
| `caddy` | HTTPS on ports 80/443, certificate renewed automatically |
| `api` | `backend/Dockerfile`, one worker |
| `db` | PostgreSQL 17, data in the `cozy_db-data` volume |

- **Address:** `https://51-170-179-54.sslip.io`. sslip.io turns the IP into a name, so no domain is needed. That URL is `RELEASE_API_BASE_URL`.
- **Login:** `ssh -i secrets/oracle-ssh.key ubuntu@51.170.179.54`, with the key from the repo's git-ignored `secrets/` folder.
- **Code:** `~/Cozy-Doubling`, a clone of this public repo.

## Files on the server only (git-ignored)

`~/Cozy-Doubling/deploy/oracle/.env`:

```properties
DOMAIN=51-170-179-54.sslip.io
POSTGRES_PASSWORD=
ENVIRONMENT=production
SECRET_KEY=
GOOGLE_CLIENT_IDS=
REVENUECAT_SECRET_KEY=
REVENUECAT_WEBHOOK_AUTH=
```

The server values come from `secrets/release.env`; `POSTGRES_PASSWORD` is random hex. Write values without comments on the same line. Copy `secrets/firebase-service-account.json` next to it.

## Everyday commands (on the server, in `~/Cozy-Doubling/deploy/oracle`)

| Task | Command |
|---|---|
| Update to the latest `main` | `git -C ~/Cozy-Doubling pull && docker compose up -d --build` |
| Status | `docker compose ps` |
| API logs | `docker compose logs -f api` |
| Restart | `docker compose restart api` |
| Backup now | `./backup.sh` |

## Backups

`backup.sh` runs every night at 03:30 from cron and keeps 14 days in `~/backups`:

```
30 3 * * * /home/ubuntu/Cozy-Doubling/deploy/oracle/backup.sh
```

These dumps live on the same VM. For a copy elsewhere, pull them now and then:

```powershell
scp -i secrets/oracle-ssh.key "ubuntu@51.170.179.54:backups/*" .\secrets\backups\
```

Restore: `gunzip -c cozy-DATE.sql.gz | docker compose exec -T db psql -U cozy -d cozy` into an empty database.

## One-time server setup (already done)

1. Oracle network: ingress TCP 22, 80, 443 from `0.0.0.0/0`.
2. Ubuntu firewall: Oracle's image rejects everything except SSH, so allow 80 and 443 before the reject rule, then `sudo netfilter-persistent save`.
3. `sudo apt install docker.io docker-compose-v2 git`, then `sudo usermod -aG docker ubuntu`.
4. `git clone https://github.com/RedPho/Cozy-Doubling.git`, add `.env` and the Firebase JSON, then `docker compose up -d --build`.
