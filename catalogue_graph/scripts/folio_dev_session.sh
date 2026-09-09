#!/usr/bin/env bash
#
# Start / end a FOLIO dev-sandbox session for the deployed sync Lambda.
#
# The sandbox is only up during working hours, so the deployed Lambda's default
# target is "prod" (see infra/adapters/main.tf). This script pairs the two manual
# steps of a testing session: bring the sandbox up, and point the Lambda's
# default at it — then put both back.
#
#   ./folio_dev_session.sh on      start the sandbox, switch FOLIO_TARGET to dev
#   ./folio_dev_session.sh off     switch FOLIO_TARGET back to prod, stop the sandbox
#   ./folio_dev_session.sh status  show the sandbox state and the current target
#
# Scope: this affects *direct* invocations of the Lambda only. The scheduled
# every-15-minutes pipeline (adapter -> EventBridge -> Step Functions) resolves
# its target in the state machine definition, which is baked at apply time from
# folio_default_target, so it stays on production throughout a session.
#
# Per-invocation targeting still works and still wins over this: a payload with
# {"folio_target": "prod"} goes to prod even mid-session.
#
# NOTE: "on" leaves the Lambda's environment differing from Terraform, which
# declares FOLIO_TARGET=prod. A `terraform apply` during a session silently puts
# the default back to prod. That is a safe direction to fail, but it does mean a
# long session can end without you noticing — `status` will tell you.
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-platform-developer}"
AWS_REGION="${AWS_REGION:-eu-west-1}"
FUNCTION_NAME="${FUNCTION_NAME:-axiell-folio-sync-adapter-lambda}"
INSTANCE_NAME="${INSTANCE_NAME:-folio-sandbox}"
export AWS_PROFILE AWS_REGION

aws_() { aws --region "$AWS_REGION" "$@"; }

# Looked up by tag rather than pinned: the sandbox has been rebuilt more than
# once, and each rebuild changes both the instance id and its private IP.
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

# The whole Variables map has to be resent, so merge rather than overwrite —
# otherwise OKAPI_SECRET_PARAM and friends are silently dropped.
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

# The url in SSM is managed by Terraform from the instance's live IP. If the box
# was rebuilt without a subsequent apply, they drift and every dev run fails with
# a connection timeout that looks like a network fault.
check_url_matches() {
  local ip="$1" url
  url=$(aws_ ssm get-parameter --with-decryption \
    --name /catalogue_pipeline/axiell-folio-sync/okapi_credentials_dev \
    --query 'Parameter.Value' --output text 2>/dev/null | jq -re '.url' 2>/dev/null || echo "")
  if [[ -n "$url" && "$url" != *"$ip"* ]]; then
    echo "WARNING: SSM dev url is '$url' but the sandbox is at $ip." >&2
    echo "         Run 'terraform apply' in infra/adapters to refresh it." >&2
  fi
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
  check_url_matches "$ip"
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
  # An unset env var is not an error: resolve_folio_target falls back to prod.
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
