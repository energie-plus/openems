#!/bin/bash
# deploy-enplus.sh
# Deploys the OpenEMS Edge JAR to an EnPlus device.
#
# Usage:
#   ./tools/deploy/deploy-enplus.sh [--rebuild-integration] <device>
#
# Options:
#   --rebuild-integration   Rebuild the integration branch from develop +
#                           INTEGRATION_FEATURES before deploying.
#                           Only allowed for device 'test'.
#
# Devices:
#   home   - ems4 / 192.168.1.224
#   test   - ems7 / 192.168.1.225
#
# Example:
#   ./tools/deploy/deploy-enplus.sh test
#   ./tools/deploy/deploy-enplus.sh --rebuild-integration test

set -e

# ---------------------------------------------------------------------------
# Features to include in the integration branch
# Add/remove branches here to control what gets tested together.
# ---------------------------------------------------------------------------
INTEGRATION_FEATURES=(
  "feature/deploy-script"
  "feature/shelly-pro-em50"
  "feature/x500-gpio-v2"
  "feature/goodwe-15k-et-export-limit"
)

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
REBUILD_INTEGRATION=false
DEVICE=""

for arg in "$@"; do
  case $arg in
    --rebuild-integration)
      REBUILD_INTEGRATION=true
      ;;
    *)
      DEVICE=$arg
      ;;
  esac
done

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
# Rebuild integration branch (optional)
# ---------------------------------------------------------------------------
if [ "$REBUILD_INTEGRATION" = true ]; then
  echo ">>> Rebuilding integration branch..."
  git checkout develop
  git pull --ff-only
  git checkout -B integration

  for branch in "${INTEGRATION_FEATURES[@]}"; do
    echo "    Merging $branch..."
    git merge --no-ff "$branch" -m "integration: merge $branch"
  done

  git push origin integration --force-with-lease
  echo ">>> Integration branch rebuilt."
  echo ""
fi

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
echo ""
echo "Tip: Update DEVICES.md to record this deployment."