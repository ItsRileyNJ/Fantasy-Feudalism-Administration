# Backups, audit logs, and recovery

[Wiki home](Home.md) · [Troubleshooting](Troubleshooting.md)

## Create an FFA snapshot

```text
/ffa backup
```

The private confirmation identifies the saved snapshot under `<world>/ffa/backups/<timestamp>/`. It includes:

- `state.json`: FFA account, shop, spawn, warp, portal, home and related state.
- `server.toml`: general FFA settings.
- `regions.toml`: authoritative protected-region definitions.
- `README.txt`: recovery restrictions.

A snapshot is refused while prepared economy transactions exist. Backups contain **no world blocks, schematics, vanilla player inventories, or other mods' town data**. Snapshot creation does not automatically prune old backups.

## A safe full-server backup

For recovery that may involve traded items and currency together, stop the server and back up the complete relevant server/world/player data consistently. Include the active FFA config, player inventories and FFA state from the same stopped-server moment. Preserve other mods' required files according to their own procedures.

A set of files copied at unrelated times while transactions continue is not an equivalent recovery point.

## Restore configuration or regions

1. Stop the server.
2. Retain copies of the current files.
3. Restore only the desired `server.toml` and/or `regions.toml` from the selected snapshot.
4. Start the server to validate them. If validation fails, inspect the named key/region and restore the retained files as necessary.

This procedure changes FFA rules/definitions, not world blocks. Do not overwrite unrelated world directories as part of an FFA config rollback.

## Do not roll back balances alone

Restoring an old `state.json` after trades can credit old currency while purchased items remain in player inventories. FFA therefore has no live snapshot-to-balance rollback command. Its own snapshot alone cannot safely undo every item transaction.

Use a matching, consistent external stopped-server backup for a full financial/inventory rollback. For an investigated individual correction, use `/ffa economy set ONLINE_PLAYER AMOUNT` and retain the audit record; this sets a total, not a delta.

## Interrupted trades and failed saves

A trade records a durable before/after journal, applies inventory changes, saves/verifies vanilla player data and commits the balance. On reconnect:

- An inventory matching the completed snapshot settles the trade once.
- An inventory matching the original snapshot cancels it.
- A mismatch locks login for administrator investigation instead of guessing or issuing replacement items.

Do not delete pending transaction records to bypass recovery. If state storage reports a real I/O failure, further state writes are locked until restart/recovery. Resolve disk space, filesystem or permission errors first, retain current files, then restart and investigate the log. Uncertain recovery may require developer assistance.

Windows Java cannot flush a directory through a FileChannel; version 0.1.1+ handles that limitation without suppressing real file-write errors. Atomic file replacement remains required. Hard power-loss durability still depends on the OS/filesystem and belongs in staging tests.

## Audit and security logs

`logging.audit` defaults to true; `logging.security` defaults to false. Files are `<world>/ffa/audit.log` and `security.log`, with numbered archives. Default size threshold: **1 MiB**; archive count: **8** plus the active file. Security notices are rate-limited, and these settings control FFA's logs, not every other mod's logs.

Records identify actor, time, action, location and result as applicable. Timestamps use ISO-8601 UTC (`Z`). Administration responses are private to the initiating source by default; there is no automatic staff-wide confirmation spam.
