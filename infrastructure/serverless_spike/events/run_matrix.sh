#!/usr/bin/env bash
# Run the write matrix against one target as one Lambda invocation per bulk size.
# Usage: run_matrix.sh TARGET OUT_DIR [FUNCTION_NAME]
# Single-document bulks are slow on serverless, so that invocation writes fewer docs per cell.

set -euo pipefail

TARGET="$1"
OUT_DIR="$2"
FUNCTION_NAME="${3:-catalogue-2026-10-14-serverless-es-bench}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(git -C "$HERE" rev-parse --show-toplevel)"
STAMP="$(date +%Y-%m-%d)"

mkdir -p "$OUT_DIR"
cd "$ROOT/catalogue_graph"

for BULK in 1 10 50 100; do
  DOCS=5000
  if [[ "$BULK" == "1" ]]; then DOCS=2000; fi
  EVENT="$(mktemp)"
  python3 -I "$HERE/make_event.py" write_matrix --target "$TARGET" --bulk-sizes "$BULK" \
    --docs-per-cell "$DOCS" --sample-count "$DOCS" --id-prefix "matrix-$STAMP" > "$EVENT"
  OUT="$OUT_DIR/$STAMP-matrix-lambda-$TARGET-b$BULK.json"
  echo "$(date +%H:%M:%S) $TARGET bulk $BULK -> $OUT"
  uv run python -m es_bench.handler invoke --function-name "$FUNCTION_NAME" --event-file "$EVENT" > "$OUT"
  python3 -I "$HERE/summarise.py" "$OUT"
  rm -f "$EVENT"
done
