#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <ids_file> [batch_size]" >&2
  exit 1
fi

IDS_FILE="$1"
BATCH_SIZE="${2:-100}"
CALM_TABLE_NAME="${CALM_TABLE_NAME:-vhs-calm-adapter}"
AWS_REGION="${AWS_REGION:-eu-west-1}"
QUEUE_URL="http://localhost:4566/000000000000/calm-transformer-queue"
AWS_PROFILE_ARG=()

if [[ ! -f "$IDS_FILE" ]]; then
  echo "No such IDs file: $IDS_FILE" >&2
  exit 1
fi

if [[ -n "${AWS_PROFILE:-}" ]]; then
  AWS_PROFILE_ARG=(--profile "$AWS_PROFILE")
fi

if ! [[ "$BATCH_SIZE" =~ ^[1-9][0-9]*$ ]]; then
  echo "batch_size must be a positive integer" >&2
  exit 1
fi

sent_count=0
missing_count=0

function enqueue_id() {
  local calm_id="$1"
  local row_fields
  local bucket
  local key
  local version
  local is_deleted
  local payload
  local wrapped_message

  row_fields="$(
    aws dynamodb get-item \
      --region "$AWS_REGION" \
      "${AWS_PROFILE_ARG[@]}" \
      --table-name "$CALM_TABLE_NAME" \
      --key "{\"id\":{\"S\":\"$calm_id\"}}" \
      --query "[Item.payload.M.bucket.S, Item.payload.M.key.S, Item.version.N, Item.isDeleted.BOOL]" \
      --output text
  )"

  read -r bucket key version is_deleted <<< "$row_fields"

  if [[ "$version" == "None" || -z "$version" ]]; then
    echo "Skipping missing CALM ID: $calm_id" >&2
    missing_count=$((missing_count + 1))
    return
  fi

  if [[ "$is_deleted" == "None" || -z "$is_deleted" ]]; then
    is_deleted="false"
  fi

  payload="$(printf '{"id":"%s","payload":{"bucket":"%s","key":"%s"},"version":%s,"isDeleted":%s}' "$calm_id" "$bucket" "$key" "$version" "$is_deleted")"
  wrapped_message="$(printf '{"Message":%s}' "$(printf '%s' "$payload" | python3 -c 'import json, sys; print(json.dumps(sys.stdin.read()))')")"

  aws --endpoint-url=http://localhost:4566 sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$wrapped_message" \
    >/dev/null

  sent_count=$((sent_count + 1))
}

function process_batch() {
  local -n ids_ref=$1
  local batch_number="$2"
  local id

  echo "Enqueueing batch $batch_number (${#ids_ref[@]} IDs)"
  for id in "${ids_ref[@]}"; do
    enqueue_id "$id"
  done
}

batch=()
batch_number=1

while IFS= read -r raw_id || [[ -n "$raw_id" ]]; do
  calm_id="$(echo "$raw_id" | xargs)"
  if [[ -z "$calm_id" ]]; then
    continue
  fi

  batch+=("$calm_id")

  if [[ ${#batch[@]} -ge "$BATCH_SIZE" ]]; then
    process_batch batch "$batch_number"
    batch=()
    batch_number=$((batch_number + 1))
  fi
done < "$IDS_FILE"

if [[ ${#batch[@]} -gt 0 ]]; then
  process_batch batch "$batch_number"
fi

echo "Done. Enqueued $sent_count IDs to $QUEUE_URL; missing IDs: $missing_count"
