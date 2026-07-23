#!/usr/bin/env bash

set -euo pipefail

PIPELINE_DATE=""
INDEX_NAME=""
OUTPUT_ENV_FILE=".env.local"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --index-name)
      INDEX_NAME="${2:-}"
      shift 2
      ;;
    --output-env-file)
      OUTPUT_ENV_FILE="${2:-}"
      shift 2
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -z "$PIPELINE_DATE" ]]; then
        PIPELINE_DATE="$1"
      else
        echo "Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -z "$PIPELINE_DATE" || -z "$INDEX_NAME" ]]; then
  echo "Usage: $0 <pipeline_date> --index-name <index_name> [--output-env-file <path>]" >&2
  exit 1
fi

AWS_REGION="${AWS_REGION:-eu-west-1}"
AWS_PROFILE_ARG=()

if [[ -n "${AWS_PROFILE:-}" ]]; then
  AWS_PROFILE_ARG=(--profile "$AWS_PROFILE")
fi

function read_secret_value() {
  local secret_id="$1"
  aws secretsmanager get-secret-value \
    --region "$AWS_REGION" \
    "${AWS_PROFILE_ARG[@]}" \
    --secret-id "$secret_id" \
    --query "SecretString" \
    --output text
}

ES_HOST="$(read_secret_value "elasticsearch/pipeline_storage_${PIPELINE_DATE}/private_host")"
ES_PORT="$(read_secret_value "elasticsearch/pipeline_storage_${PIPELINE_DATE}/port")"
ES_PROTOCOL="$(read_secret_value "elasticsearch/pipeline_storage_${PIPELINE_DATE}/protocol")"
ES_APIKEY="$(read_secret_value "elasticsearch/pipeline_storage_${PIPELINE_DATE}/transformer/api_key")"
ES_INDEX="$INDEX_NAME"

cat > "$OUTPUT_ENV_FILE" <<EOF
es_host=$ES_HOST
es_port=$ES_PORT
es_protocol=$ES_PROTOCOL
es_apikey=$ES_APIKEY
es_index=$ES_INDEX
EOF

echo "Wrote ES config to $OUTPUT_ENV_FILE"
