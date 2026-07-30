#!/usr/bin/env bash
#
# Run the Axiell -> FOLIO sync locally. Targets prod (EBSCO) FOLIO by default;
# set FOLIO_TARGET=dev to target the folio-dev-server sandbox instead.
#
# For prod, fetches the OKAPI credentials SecureString from SSM. For dev, points
# at the sandbox over the SSM tunnel and reads diku_admin from Secrets Manager.
# Either way it runs adapters.steps.axiell_folio_sync.axiell_folio_sync via uv.
#
# By default this is a DRY RUN (no writes to FOLIO). Pass --live to write.
#
# Set CHANGESET_IDS to the changeset id(s) to process (space-separated for
# multiple), OR set SAMPLE_LIMIT=N to instead process a sample of N active
# records (no changesets — a smoke test that reads no deletion facts).
# CHANGESET_IDS wins if both are set. Any extra args are forwarded to the module:
#   CHANGESET_IDS=<changeset_id> ./run_axiell_folio_sync_local.sh              # Dry run (default)
#   CHANGESET_IDS=<changeset_id> ./run_axiell_folio_sync_local.sh --live       # Write to prod FOLIO
#   CHANGESET_IDS="<changeset_id> <changeset_id2>" ./run_axiell_folio_sync_local.sh     # Multiple changesets
#   SAMPLE_LIMIT=10 ./run_axiell_folio_sync_local.sh --live            # Sample 10 active records
#   FOLIO_TARGET=dev CHANGESET_IDS=<changeset_id> ./run_axiell_folio_sync_local.sh  # Target dev FOLIO
#   AWS_PROFILE=my-profile CHANGESET_IDS=<changeset_id> ./run_axiell_folio_sync_local.sh --live
#
# FOLIO_TARGET=dev expects an SSM tunnel to the sandbox exposing Kong on
# localhost:8000 (run scripts/tunnel.sh in the folio-dev-server repo first).
#
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-platform-developer}"
AWS_REGION="${AWS_REGION:-eu-west-1}"
FOLIO_TARGET="${FOLIO_TARGET:-prod}"
OKAPI_SECRET_PARAM="${OKAPI_SECRET_PARAM:-/catalogue_pipeline/axiell-folio-sync/okapi_credentials}"
JOB_ID="${JOB_ID:-local-test-$(date +%s)}"
CHANGESET_IDS="${CHANGESET_IDS:-}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-}"

if [[ -z "$CHANGESET_IDS" && -z "$SAMPLE_LIMIT" ]]; then
  echo "Set CHANGESET_IDS (space-separated for multiple) or SAMPLE_LIMIT=N." >&2
  echo "Usage: CHANGESET_IDS=<id> $0 [--live] [extra args]" >&2
  echo "   or: SAMPLE_LIMIT=<n> $0 [--live] [extra args]" >&2
  exit 2
fi

export AWS_PROFILE AWS_REGION

# Resolve repo layout: this script lives in catalogue_graph/scripts/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CATALOGUE_GRAPH_DIR="$(dirname "$SCRIPT_DIR")"

case "$FOLIO_TARGET" in
  prod)
    echo "Fetching OKAPI credentials from SSM: $OKAPI_SECRET_PARAM (profile=$AWS_PROFILE region=$AWS_REGION)" >&2
    CREDS="$(aws ssm get-parameter \
      --name "$OKAPI_SECRET_PARAM" \
      --with-decryption \
      --query 'Parameter.Value' \
      --output text)"

    OKAPI_URL="$(echo "$CREDS" | jq -re '.url // empty')"
    OKAPI_TENANT="$(echo "$CREDS" | jq -re '.tenant // empty')"
    OKAPI_USERNAME="$(echo "$CREDS" | jq -re '.username // empty')"
    OKAPI_PASSWORD="$(echo "$CREDS" | jq -re '.password // empty')"
    ;;
  dev)
    # folio-dev-server sandbox, reached over the SSM tunnel (scripts/tunnel.sh in the
    # folio-dev-server repo forwards Kong :8000 to localhost:8000). Password comes from
    # the same Secrets Manager entry scripts/password.sh reads.
    DEV_OKAPI_URL="${DEV_OKAPI_URL:-http://localhost:8000}"
    DEV_OKAPI_TENANT="${DEV_OKAPI_TENANT:-diku}"
    DEV_OKAPI_USERNAME="${DEV_OKAPI_USERNAME:-diku_admin}"
    DEV_SECRET_NAME="${DEV_SECRET_NAME:-folio-sandbox/diku-admin-password}"

    echo "Fetching dev diku_admin password from Secrets Manager: $DEV_SECRET_NAME (profile=$AWS_PROFILE region=$AWS_REGION)" >&2
    OKAPI_PASSWORD="$(aws secretsmanager get-secret-value \
      --secret-id "$DEV_SECRET_NAME" \
      --query SecretString \
      --output text)"
    OKAPI_URL="$DEV_OKAPI_URL"
    OKAPI_TENANT="$DEV_OKAPI_TENANT"
    OKAPI_USERNAME="$DEV_OKAPI_USERNAME"
    echo "NOTE: dev target needs an SSM tunnel to the sandbox exposing $OKAPI_URL (scripts/tunnel.sh)." >&2
    ;;
  *)
    echo "Unknown FOLIO_TARGET '$FOLIO_TARGET' (expected 'prod' or 'dev')." >&2
    exit 2
    ;;
esac
export OKAPI_URL OKAPI_TENANT OKAPI_USERNAME OKAPI_PASSWORD

echo "Target FOLIO ($FOLIO_TARGET): $OKAPI_URL (tenant=$OKAPI_TENANT)" >&2

# Default args; forward anything the caller passes. Callers can add --live,
# override --job-id, etc. With CHANGESET_IDS set we run changeset mode (the ids
# are word-split intentionally so each becomes a --changeset-ids value); with
# only SAMPLE_LIMIT set we run sample mode over active records.
DEFAULT_ARGS=(
  --use-cli
  --job-id "$JOB_ID"
  --use-rest-api-table
)
if [[ -n "$CHANGESET_IDS" ]]; then
  # shellcheck disable=SC2206
  CHANGESET_ID_ARGS=($CHANGESET_IDS)
  DEFAULT_ARGS+=(--changeset-ids "${CHANGESET_ID_ARGS[@]}")
  echo "Mode: changesets (${CHANGESET_ID_ARGS[*]})" >&2
else
  DEFAULT_ARGS+=(--sample-limit "$SAMPLE_LIMIT")
  echo "Mode: sample of $SAMPLE_LIMIT active record(s)" >&2
fi

cd "$CATALOGUE_GRAPH_DIR"
echo "Running sync (job-id=$JOB_ID). Dry run unless --live is passed." >&2
exec uv run python -m adapters.steps.axiell_folio_sync.axiell_folio_sync \
  "${DEFAULT_ARGS[@]}" "$@"
