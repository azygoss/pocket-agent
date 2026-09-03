# Backup / Restore (P18)
- DB: `deploy/docker-compose/backup.sh` (pg_dump), geri `restore.sh <dosya>`.
- Volumes: `pgdata` snapshot.
- Kullanıcı silmede cascade wipe: `store.WipeUser` + TTL süpürme (test: TestWipeUser).
