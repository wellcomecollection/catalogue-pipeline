#!/usr/bin/env bash
#
# Start and end a FOLIO dev-sandbox session for the deployed sync Lambda.
#
#   ./folio_dev_session.sh on      start the sandbox, switch FOLIO_TARGET to dev
#   ./folio_dev_session.sh off     switch FOLIO_TARGET back to prod, stop the sandbox
#   ./folio_dev_session.sh status  show the sandbox state and the current target
#
# This affects direct invocations only. Scheduled runs take their target from the
# state machine, which is baked at apply time, so they stay on production. A
# folio_target on the event still wins over both.
#
# "on" leaves the Lambda's environment out of step with Terraform, so a
# terraform apply mid-session puts FOLIO_TARGET back to prod. Run `status` to
# check.
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-platform-developer}"
AWS_REGION="${AWS_REGION:-eu-west-1}"
FUNCTION_NAME="${FUNCTION_NAME:-axiell-folio-sync-adapter-lambda}"
INSTANCE_NAME="${INSTANCE_NAME:-folio-sandbox}"
DEV_PARAM="${DEV_PARAM:-/catalogue_pipeline/axiell-folio-sync/okapi_credentials_dev}"
PASSWORD_SECRET_ID="${PASSWORD_SECRET_ID:-folio-sandbox/diku-admin-password}"
TENANT="${TENANT:-diku}"
USERNAME="${USERNAME:-diku_admin}"
API_PORT="${API_PORT:-8000}"
export AWS_PROFILE AWS_REGION

aws_() { aws --region "$AWS_REGION" "$@"; }

# Looked up by tag, because a rebuild changes both the id and the private IP.
instance_id() {
  aws_ ec2 describe-instances \
    --filters "Name=tag:Name,Values=$INSTANCE_NAME" \
              "Name=instance-state-name,Values=running,stopped,pending,stopping" \
    --query 'Reservations[].Instances[].InstanceId' --output text
}

instance_state() {
  aws_ ec2 describe-instances --instance-ids "$1" \
    --query 'Reservations[].Instances[].State.Name' --output text
}

instance_ip() {
  aws_ ec2 describe-instances --instance-ids "$1" \
    --query 'Reservations[].Instances[].PrivateIpAddress' --output text
}

current_target() {
  aws_ lambda get-function-configuration --function-name "$FUNCTION_NAME" \
    --query 'Environment.Variables.FOLIO_TARGET' --output text
}

# The whole Variables map is resent, so merge rather than overwrite. Overwriting
# drops OKAPI_SECRET_PARAM and the rest.
set_target() {
  local target="$1" env_json
  env_json=$(aws_ lambda get-function-configuration --function-name "$FUNCTION_NAME" \
    --query 'Environment.Variables' --output json |
    jq -c --arg t "$target" '. + {FOLIO_TARGET: $t}')

  aws_ lambda update-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --environment "{\"Variables\":$env_json}" >/dev/null
  aws_ lambda wait function-updated --function-name "$FUNCTION_NAME"
  echo "FOLIO_TARGET is now '$target'"
}

# Populate the dev OKAPI SecureString, which Terraform only seeds with
# placeholders. Rewritten on every
# `on`, because a rebuilt sandbox changes the url and a stale one fails as a
# connection timeout.
refresh_dev_credentials() {
  local ip="$1" pw req
  pw=$(aws_ secretsmanager get-secret-value --secret-id "$PASSWORD_SECRET_ID" \
    --query SecretString --output text)

  # Passed by file, not on the command line, where `ps` would expose it.
  req=$(mktemp); chmod 600 "$req"
  trap 'rm -f "$req"' RETURN

  jq -n --arg name "$DEV_PARAM" \
    --arg v "$(jq -nc --arg url "http://$ip:$API_PORT" --arg t "$TENANT" \
      --arg u "$USERNAME" --arg p "$pw" \
      '{url:$url, tenant:$t, username:$u, password:$p}')" \
    '{Name:$name, Value:$v, Type:"SecureString", Overwrite:true}' >"$req"

  aws_ ssm put-parameter --cli-input-json "file://$req" >/dev/null
  echo "Refreshed $DEV_PARAM (url http://$ip:$API_PORT, tenant $TENANT)"
}

id=$(instance_id)
if [[ -z "$id" || "$id" == "None" ]]; then
  echo "No EC2 instance tagged Name=$INSTANCE_NAME found." >&2
  exit 1
fi

case "${1:-status}" in
on)
  state=$(instance_state "$id")
  if [[ "$state" != "running" ]]; then
    echo "Starting $id (was $state)..."
    aws_ ec2 start-instances --instance-ids "$id" >/dev/null
    aws_ ec2 wait instance-running --instance-ids "$id"
  fi
  ip=$(instance_ip "$id")
  echo "Sandbox $id running at $ip"
  refresh_dev_credentials "$ip"
  set_target dev
  echo
  echo "NOTE: FOLIO (Kong on :8000) takes a few minutes to come up after the"
  echo "      instance does. An invoke before then fails with a connect timeout."
  ;;
off)
  set_target prod
  echo "Stopping $id..."
  aws_ ec2 stop-instances --instance-ids "$id" >/dev/null
  echo "Stop requested. FOLIO_TARGET is back to prod."
  ;;
status)
  target=$(current_target)
  # Unset is not an error. resolve_folio_target falls back to prod.
  [[ "$target" == "None" || -z "$target" ]] && target="unset (resolves to prod)"
  printf 'sandbox      %s (%s)\n' "$id" "$(instance_state "$id")"
  printf 'private ip   %s\n' "$(instance_ip "$id")"
  printf 'FOLIO_TARGET %s\n' "$target"
  ;;
*)
  echo "Usage: $0 {on|off|status}" >&2
  exit 1
  ;;
esac
