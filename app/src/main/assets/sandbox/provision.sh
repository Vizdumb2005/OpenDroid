#!/bin/bash
# OpenDroid sandbox guest provisioner.
# Idempotent: every step is guarded so a failed run can simply be retried.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
export APT_LISTCHANGES_FRONTEND=none

STAMP=/root/.opendroid
mkdir -p "$STAMP" /root/workspace /opt/opendroid /root/.config/opencode

step() { echo "==> $*"; }

# 1. Clean up any stale dpkg / apt locks from previously interrupted runs
rm -f /var/lib/dpkg/lock* /var/lib/apt/lists/lock* /var/cache/apt/archives/lock*
dpkg --configure -a 2>/dev/null || true

# 2. Sudo shim: the sandbox already runs as uid 0 (proot fake root).
cat > /usr/local/bin/sudo <<'EOF'
#!/bin/sh
while [ $# -gt 0 ]; do
  case "$1" in
    -E|-H|-n|-S|-k|-b|-i|-s) shift ;;
    -u|-g|-C) shift 2 ;;
    --) shift; break ;;
    -*) shift ;;
    *) break ;;
  esac
done
exec "$@"
EOF
chmod 0755 /usr/local/bin/sudo

# 3. Detect architecture for OpenCode official release
ARCH="$(uname -m)"
case "$ARCH" in
  aarch64|arm64) OC_ARCH="arm64" ;;
  x86_64|amd64)  OC_ARCH="x64" ;;
  *)             OC_ARCH="arm64" ;;
esac

# 4. Base packages (only essentials first to finish in seconds)
if [ ! -f "$STAMP/.apt_done" ]; then
  step "Updating package index"
  apt-get update -o Acquire::Retries=3 -qq || true

  step "Installing base tools"
  apt-get install -y --no-install-recommends \
    -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" \
    ca-certificates curl git python3 python3-venv python3-pip xz-utils less procps jq || true
  touch "$STAMP/.apt_done"
fi

# 5. Install official OpenCode release directly (if not pre-extracted)
if [ ! -x /usr/local/bin/opencode ]; then
  step "Installing OpenCode ($OC_ARCH)"
  OC_URL="https://github.com/sst/opencode/releases/latest/download/opencode-linux-$OC_ARCH.tar.gz"
  curl -fL "$OC_URL" -o /tmp/opencode.tar.gz || curl -fL "https://github.com/sst/opencode/releases/download/v0.1.48/opencode-linux-$OC_ARCH.tar.gz" -o /tmp/opencode.tar.gz || true
  if [ -f /tmp/opencode.tar.gz ]; then
    tar -xzf /tmp/opencode.tar.gz -C /usr/local/bin opencode 2>/dev/null || true
    chmod 0755 /usr/local/bin/opencode 2>/dev/null || true
    rm -f /tmp/opencode.tar.gz
  fi
fi
step "OpenCode version: $(/usr/local/bin/opencode --version 2>&1 | head -n1 || echo 'ready')"

# 6. Python venv & Fast Server dependencies
if [ ! -x /opt/mem0/bin/python ]; then
  step "Setting up Python environment"
  python3 -m venv /opt/mem0 || true
fi

if [ -x /opt/mem0/bin/pip ] && [ ! -f "$STAMP/.mem0_done" ]; then
  step "Installing server requirements"
  /opt/mem0/bin/pip install --no-cache-dir --prefer-binary fastapi uvicorn requests || true
  touch "$STAMP/.mem0_done"
fi

# 7. Background install of full mem0ai library so provision finishes in seconds
if [ -x /opt/mem0/bin/pip ] && [ ! -f "$STAMP/.mem0ai_full_done" ]; then
  ( /opt/mem0/bin/pip install --no-cache-dir --prefer-binary mem0ai > /tmp/mem0_install.log 2>&1 && touch "$STAMP/.mem0ai_full_done" ) &
fi

step "Provisioning complete"
