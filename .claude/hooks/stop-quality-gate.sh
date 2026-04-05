#!/bin/bash
set -euo pipefail

cd "$CLAUDE_PROJECT_DIR"

OUTPUT=$(./gradlew --no-daemon spotlessCheck test 2>&1)
if [ $? -ne 0 ]; then
  echo "[Quality Gate] spotlessCheck/test failed." >&2
  echo "$OUTPUT" >&2
  exit 2
fi

exit 0
