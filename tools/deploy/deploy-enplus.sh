#!/bin/bash
# deploy-enplus.sh
# Deploys the OpenEMS Edge JAR to an EnPlus device.
#
# Usage:
#   ./tools/deploy/deploy-enplus.sh <device>
#
# Devices:
#   home   - ems4 / 192.168.1.224
#   test   - ems7 / 192.168.1.225
#
# Example:
#   ./tools/deploy/deploy-enplus.sh test

set -e

# ---------------------------------------------------------------------------
# Device configuration
# ---------------------------------------------------------------------------
DEVICE=$1

case $DEVICE in
  home)
    HOST="root@192.168.1.224"
    NAME="ems4"
    ;;
  test)
    HOST="root@192.168.1.225"
    NAME="ems7"
    ;;
  *)
    echo "ERROR: Unknown device: '$DEVICE'"
    echo ""
    echo "Usage: $0 <device>"
    echo "Available devices: home, test"
    exit 1
    ;;
esac

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
LOCAL_JAR="build/openems-edge.jar"
REMOTE_DIR="/opt/openems"
REMOTE_JAR="$REMOTE_DIR/openems-edge.jar"
BACKUP_JAR="$REMOTE_DIR/openems-edge.jar_bak$(date +%Y%m%d)"

# ---------------------------------------------------------------------------
# Git info
# ---------------------------------------------------------------------------
GIT_DESC=$(git describe --tags --always)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

echo "=========================================="
echo "  EnPlus OpenEMS Deploy"
echo "=========================================="
echo "  Device  : $NAME ($HOST)"
echo "  Version : $GIT_DESC"
echo "  Branch  : $GIT_BRANCH"
echo "=========================================="
echo ""

# ---------------------------------------------------------------------------
# Check JAR exists
# ---------------------------------------------------------------------------
if [ ! -f "$LOCAL_JAR" ]; then
  echo "ERROR: JAR not found at $LOCAL_JAR"
  echo "Run the build first:"
  echo "  ./gradlew buildEdge"
  exit 1
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
echo "[1/4] Building..."
./gradlew buildEdge
echo "      Done."
echo ""

# ---------------------------------------------------------------------------
# Backup existing JAR on device
# ---------------------------------------------------------------------------
echo "[2/4] Creating backup on $NAME..."
ssh "$HOST" "cp $REMOTE_JAR $BACKUP_JAR"
echo "      Backup: $BACKUP_JAR"
echo ""

# ---------------------------------------------------------------------------
# Upload
# ---------------------------------------------------------------------------
echo "[3/4] Uploading JAR to $NAME..."
scp "$LOCAL_JAR" "$HOST:$REMOTE_JAR"
echo "      Done."
echo ""

# ---------------------------------------------------------------------------
# Restart service
# ---------------------------------------------------------------------------
echo "[4/4] Restarting openems.service on $NAME..."
ssh "$HOST" "systemctl restart openems.service"
echo "      Done."
echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "=========================================="
echo "  Deploy successful!"
echo "  $NAME is now running $GIT_DESC"
echo "=========================================="