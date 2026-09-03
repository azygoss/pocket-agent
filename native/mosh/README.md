# Mosh (P07)
Upstream: https://github.com/mobile-shell/mosh (GPL-3.0-only).
Pin revision in decisions.tsv before binary build. No prebuilt .so in repo.
Bootstrap: SSH runs `mosh-server new -p <range>`, key travels inside SSH (zeroized, never disk/log).
Reproducible hashes recorded in tests/perf/mosh-*.log.
