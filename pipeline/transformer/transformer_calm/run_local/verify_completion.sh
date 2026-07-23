#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 3 ]]; then
  echo "Usage: $0 <ids_file> [timeout_seconds] [poll_seconds]" >&2
  exit 1
fi

IDS_FILE="$1"
TIMEOUT_SECONDS="${2:-600}"
POLL_SECONDS="${3:-10}"

QUEUE_URL="http://localhost:4566/000000000000/calm-transformer-queue"
AWS_REGION="${AWS_REGION:-eu-west-1}"
AWS_PROFILE_ARG=()

if [[ ! -f "$IDS_FILE" ]]; then
  echo "No such IDs file: $IDS_FILE" >&2
  exit 1
fi

if [[ -f ".env.local" ]]; then
  # shellcheck disable=SC1091
  source .env.local
fi

required_env_vars=(es_protocol es_host es_port es_index es_apikey)
for v in "${required_env_vars[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "Missing required env var: $v (expected from .env.local)" >&2
    exit 1
  fi
done

if [[ -n "${AWS_PROFILE:-}" ]]; then
  AWS_PROFILE_ARG=(--profile "$AWS_PROFILE")
fi

if ! [[ "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "timeout_seconds must be a positive integer" >&2
  exit 1
fi

if ! [[ "$POLL_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "poll_seconds must be a positive integer" >&2
  exit 1
fi

expected_count="$(grep -v '^[[:space:]]*$' "$IDS_FILE" | wc -l | tr -d ' ')"

if [[ "$expected_count" -eq 0 ]]; then
  echo "IDs file contains no non-empty IDs: $IDS_FILE" >&2
  exit 1
fi

echo "Waiting for queue to drain..."
start_epoch="$(date +%s)"
while true; do
  attrs_json="$(
    aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
      --queue-url "$QUEUE_URL" \
      --region "$AWS_REGION" \
      "${AWS_PROFILE_ARG[@]}" \
      --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
      --output json
  )"

  visible="$(printf '%s' "$attrs_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Attributes"].get("ApproximateNumberOfMessages","0"))')"
  in_flight="$(printf '%s' "$attrs_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Attributes"].get("ApproximateNumberOfMessagesNotVisible","0"))')"
  echo "Queue status: visible=$visible in_flight=$in_flight"

  if [[ "$visible" == "0" && "$in_flight" == "0" ]]; then
    break
  fi

  now_epoch="$(date +%s)"
  elapsed="$((now_epoch - start_epoch))"
  if (( elapsed >= TIMEOUT_SECONDS )); then
    echo "Timed out waiting for queue to drain after ${TIMEOUT_SECONDS}s" >&2
    exit 1
  fi

  sleep "$POLL_SECONDS"
done

echo "Queue drained; checking Elasticsearch index $es_index ..."

missing_ids_file="$(mktemp)"

readarray -t verification_counts < <(python3 - "$IDS_FILE" "$missing_ids_file" <<'PY'
import json
import os
import ssl
import sys
import urllib.request

ids_path = sys.argv[1]
missing_path = sys.argv[2]

es_protocol = os.environ["es_protocol"]
es_host = os.environ["es_host"]
es_port = os.environ["es_port"]
es_index = os.environ["es_index"]
es_apikey = os.environ["es_apikey"]

with open(ids_path, "r", encoding="utf-8") as f:
    ids = [line.strip() for line in f if line.strip()]

doc_ids = [f"Work[calm-record-id/{i}]" for i in ids]

url = f"{es_protocol}://{es_host}:{es_port}/{es_index}/_mget"
headers = {
    "Authorization": f"ApiKey {es_apikey}",
    "Content-Type": "application/json",
}

missing = []
batch_size = 500

context = ssl.create_default_context()

for start in range(0, len(doc_ids), batch_size):
    chunk = doc_ids[start : start + batch_size]
    payload = json.dumps({"ids": chunk}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    with urllib.request.urlopen(req, context=context) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    for doc in body.get("docs", []):
        if not doc.get("found", False):
            missing.append(doc.get("_id", "<unknown>"))

with open(missing_path, "w", encoding="utf-8") as out:
    for m in missing:
        out.write(f"{m}\n")

print(len(doc_ids))
print(len(missing))
PY
)

total_docs="${verification_counts[0]}"
missing_docs="${verification_counts[1]}"
found_docs="$((total_docs - missing_docs))"

echo "Elasticsearch coverage: found=$found_docs expected=$total_docs missing=$missing_docs"

if [[ "$missing_docs" -gt 0 ]]; then
  echo "First missing IDs:"
  head -n 20 "$missing_ids_file" | cat
fi

rm -f "$missing_ids_file"

echo "Checking transformer logs for errors..."
error_lines="$(
  docker compose -f local.docker-compose.yml logs calm-transformer 2>/dev/null \
    | grep -nE "DecodePayloadError|StoreReadError|TransformerError|ERROR|Exception" || true
)"

if [[ -n "$error_lines" ]]; then
  echo "Potential transformer errors detected:"
  echo "$error_lines"
  exit 1
fi

if [[ "$missing_docs" -gt 0 ]]; then
  exit 1
fi

echo "Verification passed: queue is drained, no transformer errors found, and all IDs are indexed."
