#!/usr/bin/env bash
# Create the Identifiers API's read-only database user on an id-minter cluster
# and write its credentials into the secret Terraform created for it
# (platform#6533).
#
# Runs as the cluster master user, over the Data API so it needs no VPC access.
# Re-running resets the password and rewrites the secret. Run it once per cluster
# the API reads: again for production, and again for the FOLIO registry.
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

DB_USER="identifiers_api_read"
DB_NAME="identifiers"
DB_TABLE="identifiers"

# Kept in step with excludeCharacters in
# modules/id-minter-rds/identifiers_api_credential.tf, so rotated passwords stay
# compatible. Change them together.
EXCLUDE_CHARACTERS="\"'@/\\\`"

REQUEST_FILE="$(mktemp)"
chmod 600 "${REQUEST_FILE}"
trap 'rm -f "${REQUEST_FILE}"' EXIT

echo "Resolving ${CLUSTER_IDENTIFIER}"

read -r CLUSTER_ARN MASTER_SECRET_ARN DB_HOST DB_PORT < <(
  aws rds describe-db-clusters \
    --db-cluster-identifier "${CLUSTER_IDENTIFIER}" \
    --query 'DBClusters[0].[DBClusterArn,MasterUserSecret.SecretArn,Endpoint,Port]' \
    --output text
)

if [[ -z "${MASTER_SECRET_ARN}" || "${MASTER_SECRET_ARN}" == "None" ]]; then
  echo "No master user secret on ${CLUSTER_IDENTIFIER}" >&2
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

PASSWORD="$(
  aws secretsmanager get-random-password \
    --password-length 32 \
    --exclude-characters "${EXCLUDE_CHARACTERS}" \
    --require-each-included-type \
    --query RandomPassword \
    --output text
)"

echo "Creating ${DB_USER} and granting SELECT on ${DB_NAME}.${DB_TABLE}"

# CREATE then ALTER so a re-run sets the password whether or not the user exists.
run_as_master <<< "CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${PASSWORD}'"
run_as_master <<< "ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${PASSWORD}'"
run_as_master <<< "GRANT SELECT ON \`${DB_NAME}\`.\`${DB_TABLE}\` TO '${DB_USER}'@'%'"

echo "Writing ${SECRET_ID}"

# engine, host, port and dbname are for the rotation function; the Data API reads
# only username and password.
jq -n \
  --arg secretId "${SECRET_ID}" \
  --arg host "${DB_HOST}" \
  --argjson port "${DB_PORT}" \
  --arg username "${DB_USER}" \
  --arg dbname "${DB_NAME}" \
  --rawfile password <(printf '%s' "${PASSWORD}") \
  '{
    SecretId: $secretId,
    SecretString: ({
      engine: "mysql",
      host: $host,
      port: $port,
      username: $username,
      password: $password,
      dbname: $dbname
    } | tostring)
  }' > "${REQUEST_FILE}"

SECRET_ARN="$(
  aws secretsmanager put-secret-value \
    --cli-input-json "file://${REQUEST_FILE}" \
    --no-cli-pager \
    --query ARN \
    --output text
)"

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
echo "Done. Secret: ${SECRET_ARN}"
