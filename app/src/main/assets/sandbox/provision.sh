#!/usr/bin/env bash
# OpenDroid guest provisioning. Runs inside the proot Ubuntu rootfs as (fake) root.
# Idempotent: every step is guarded so a failed run can simply be retried.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
STAMP=/root/.opendroid
mkdir -p "$STAMP" /root/workspace /opt/opendroid /root/.config/opencode

step() { echo "==> $*"; }

step "Updating package index"
apt-get update

step "Installing base tools"
apt-get install -y --no-install-recommends \
  ca-certificates curl git ripgrep python3 python3-venv python3-pip \
  unzip xz-utils less nano procps iproute2 iputils-ping openssh-client jq

# proot cannot honour setuid, so the real sudo package cannot work. proot already maps the user
# to uid 0, so this shim gives `sudo <cmd>` the expected behaviour.
cat > /usr/local/bin/sudo <<'EOF'
#!/bin/sh
# OpenDroid sudo shim: the sandbox already runs as uid 0 (proot fake root).
while [ $# -gt 0 ]; do
  case "$1" in
    -E|-H|-n|-S|-k|-b|-i|-s) shift ;;
    -u|-g|-C|-h|-p) shift 2 ;;
    --) shift; break ;;
    -*) shift ;;
    *) break ;;
  esac
done
[ $# -eq 0 ] && exec /bin/bash
exec "$@"
EOF
chmod 0755 /usr/local/bin/sudo

step "Resolving latest official OpenCode release"
REL_JSON="$(curl -fsSL -H 'User-Agent: opendroid' https://api.github.com/repos/sst/opencode/releases/latest)"
OC_TAG="$(echo "$REL_JSON" | jq -r .tag_name)"
OC_URL="$(echo "$REL_JSON" | jq -r '.assets[] | select(.name=="opencode-linux-arm64.tar.gz") | .browser_download_url')"
OC_SHA="$(echo "$REL_JSON" | jq -r '.assets[] | select(.name=="opencode-linux-arm64.tar.gz") | .digest // empty' | sed 's/^sha256://')"
if [ -z "$OC_URL" ]; then echo "ERROR: opencode-linux-arm64.tar.gz not found in latest release"; exit 1; fi

if [ ! -x /usr/local/bin/opencode ] || [ "$(cat $STAMP/opencode.tag 2>/dev/null || true)" != "$OC_TAG" ]; then
  step "Installing OpenCode $OC_TAG (official linux-arm64 build)"
  curl -fL "$OC_URL" -o /tmp/opencode.tar.gz
  if [ -n "$OC_SHA" ]; then
    echo "$OC_SHA  /tmp/opencode.tar.gz" | sha256sum -c -
  else
    echo "warning: release has no published digest; skipping checksum"
  fi
  tar -xzf /tmp/opencode.tar.gz -C /usr/local/bin opencode
  chmod 0755 /usr/local/bin/opencode
  rm -f /tmp/opencode.tar.gz
  echo "$OC_TAG" > "$STAMP/opencode.tag"
fi
step "OpenCode version: $(/usr/local/bin/opencode --version 2>&1 | head -n1)"

step "Installing Mem0 (python venv)"
if [ ! -x /opt/mem0/bin/python ]; then
  python3 -m venv /opt/mem0
fi
/opt/mem0/bin/pip install --no-cache-dir --upgrade pip
/opt/mem0/bin/pip install --no-cache-dir mem0ai fastapi "uvicorn[standard]" mcp
step "Mem0 installed: $(/opt/mem0/bin/pip show mem0ai | awk '/^Version/{print $2}')"

step "Provisioning complete"
