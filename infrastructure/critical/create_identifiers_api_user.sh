#!/usr/bin/env bash
# Make an id-minter cluster agree with the Identifiers API's credential secret
# (platform#6533): create the read-only MySQL user if it is missing, set its
# password to the one Terraform generated, and grant it SELECT on one table.
#
# Run after applying Terraform, and again after changing the password there.
# Re-running is safe. It runs as the cluster master user over the Data API, so it
# needs no VPC access.
#
# Usage:
#   ./create_identifiers_api_user.sh \
#     identifiers-v2-serverless-2026-07-03 \
#     rds/identifiers-v2-serverless-2026-07-03/identifiers_api_read

set -o errexit
set -o nounset
set -o pipefail

CLUSTER_IDENTIFIER="${1:?usage: $0 <cluster-identifier> <secret-id>}"
SECRET_ID="${2:?usage: $0 <cluster-identifier> <secret-id>}"

DB_NAME="identifiers"
DB_TABLE="identifiers"

REQUEST_FILE="$(mktemp)"
chmod 600 "${REQUEST_FILE}"
trap 'rm -f "${REQUEST_FILE}"' EXIT

echo "Resolving ${CLUSTER_IDENTIFIER}"

read -r CLUSTER_ARN MASTER_SECRET_ARN < <(
  aws rds describe-db-clusters \
    --db-cluster-identifier "${CLUSTER_IDENTIFIER}" \
    --query 'DBClusters[0].[DBClusterArn,MasterUserSecret.SecretArn]' \
    --output text
)

if [[ -z "${MASTER_SECRET_ARN}" || "${MASTER_SECRET_ARN}" == "None" ]]; then
  echo "No master user secret on ${CLUSTER_IDENTIFIER}" >&2
  exit 1
fi

echo "Reading ${SECRET_ID}"

SECRET="$(aws secretsmanager get-secret-value --secret-id "${SECRET_ID}" --output json)"
SECRET_ARN="$(printf '%s' "${SECRET}" | jq -r '.ARN')"
DB_USER="$(printf '%s' "${SECRET}" | jq -r '.SecretString | fromjson | .username')"
PASSWORD="$(printf '%s' "${SECRET}" | jq -r '.SecretString | fromjson | .password')"

if [[ -z "${DB_USER}" || "${DB_USER}" == "null" ]]; then
  echo "${SECRET_ID} has no username; has Terraform been applied?" >&2
  exit 1
fi

# Statement on stdin, request in a file, so a password-bearing statement never
# reaches an argument list. continueAfterTimeout is advised for DDL.
run_as_master() {
  jq -Rs \
    --arg resourceArn "${CLUSTER_ARN}" \
    --arg secretArn "${MASTER_SECRET_ARN}" \
    --arg database "${DB_NAME}" \
    '{
      resourceArn: $resourceArn,
      secretArn: $secretArn,
      database: $database,
      continueAfterTimeout: true,
      sql: (. | rtrimstr("\n"))
    }' > "${REQUEST_FILE}"

  aws rds-data execute-statement \
    --cli-input-json "file://${REQUEST_FILE}" \
    --no-cli-pager \
    >/dev/null
}

echo "Setting up ${DB_USER} with SELECT on ${DB_NAME}.${DB_TABLE}"

# CREATE then ALTER so the password is set whether or not the user exists, and
# REVOKE before GRANT so the result is exactly SELECT rather than SELECT plus
# whatever was there before.
run_as_master <<< "CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${PASSWORD}'"
run_as_master <<< "ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${PASSWORD}'"
run_as_master <<< "REVOKE IF EXISTS ALL PRIVILEGES, GRANT OPTION FROM '${DB_USER}'@'%'"
run_as_master <<< "GRANT SELECT ON \`${DB_NAME}\`.\`${DB_TABLE}\` TO '${DB_USER}'@'%'"

echo "Verifying ${DB_USER} can read"

aws rds-data execute-statement \
  --resource-arn "${CLUSTER_ARN}" \
  --secret-arn "${SECRET_ARN}" \
  --database "${DB_NAME}" \
  --no-cli-pager \
  --sql "SELECT CanonicalId FROM ${DB_TABLE} LIMIT 1" \
  >/dev/null

echo "Verifying ${DB_USER} cannot write"

# WHERE 1 = 0 matches nothing, so this cannot remove a row even if the grant were
# wider than intended.
if delete_error="$(
  aws rds-data execute-statement \
    --resource-arn "${CLUSTER_ARN}" \
    --secret-arn "${SECRET_ARN}" \
    --database "${DB_NAME}" \
    --no-cli-pager \
    --sql "DELETE FROM ${DB_TABLE} WHERE 1 = 0" 2>&1
)"; then
  echo "${DB_USER} was allowed to delete; the grant is wider than SELECT" >&2
  exit 1
fi

echo "  refused with: ${delete_error}"
echo
echo "Done."
