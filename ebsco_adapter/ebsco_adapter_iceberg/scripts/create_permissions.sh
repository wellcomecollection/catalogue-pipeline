#!/bin/bash
# A script that grants permissions and includes explicit error handling to ensure
# failures are always visible.

# We remove 'set -e' to handle errors manually for more detailed logging.
set -o pipefail

# --- Configuration ---
# An array of principals to grant permissions to.
PRINCIPAL_ARNS=(
  "arn:aws:iam::760097843905:role/lambda-role-ebsco-adapter-loader"
  "arn:aws:iam::760097843905:role/lambda-role-ebsco-adapter-transformer"
)

# Define a multi-line template for the desired permissions for readability.
read -r -d '' PERMISSIONS_TEMPLATE <<'EOF'
[
    {
        "Principal": { "DataLakePrincipalIdentifier": "PLACEHOLDER" },
        "Resource": { 
          "Catalog": {
            "Id": "760097843905:s3tablescatalog/wellcomecollection-platform-ebsco-adapter"
          } 
        },
        "Permissions": [ "ALL" ]
    },
    {
        "Principal": { "DataLakePrincipalIdentifier": "PLACEHOLDER" },
        "Resource": {
            "Database": {
                "CatalogId": "760097843905:s3tablescatalog/wellcomecollection-platform-ebsco-adapter",
                "Name": "wellcomecollection_catalogue"
            }
        },
        "Permissions": [ "ALL" ]
    },
    {
        "Principal": { "DataLakePrincipalIdentifier": "PLACEHOLDER" },
        "Resource": {
            "Table": {
                "CatalogId": "760097843905:s3tablescatalog/wellcomecollection-platform-ebsco-adapter",
                "DatabaseName": "wellcomecollection_catalogue",
                "Name": "ebsco_adapter_table"
            }
        },
        "Permissions": [ "ALL" ]
    }
]
EOF

# --- Helper Function ---
describe_resource() {
  local resource_json=$1
  if jq -e '.Catalog' > /dev/null <<< "$resource_json"; then echo "CATALOG";
  elif jq -e '.Database' > /dev/null <<< "$resource_json"; then jq -r '"DATABASE: \(.Database.Name)"' <<< "$resource_json";
  elif jq -e '.Table' > /dev/null <<< "$resource_json"; then jq -r '"TABLE: \(.Table.DatabaseName).\(.Table.Name)"' <<< "$resource_json";
  else echo "Unknown Resource"; fi
}

# --- Script Logic ---
for principal_arn in "${PRINCIPAL_ARNS[@]}"; do
  echo "---"
  echo "Processing principal: $principal_arn"
  desired_permissions=$(jq --arg arn "$principal_arn" '(.[] | .Principal.DataLakePrincipalIdentifier) |= $arn' <<< "$PERMISSIONS_TEMPLATE")

  while IFS= read -r perm; do
    resource_json=$(jq -c '.Resource' <<< "$perm")
    permissions_list=$(jq -r '.Permissions | .[]' <<< "$perm")
    resource_desc=$(describe_resource "$resource_json")
    
    echo "  Ensuring permissions on resource: $resource_desc"
    
    # Execute the command and check its exit code immediately.
    # The '!' negates the result, so the 'if' block runs on failure.
    if ! aws lakeformation grant-permissions \
      --principal "DataLakePrincipalIdentifier=$principal_arn" \
      --resource "$resource_json" \
      --permissions $permissions_list; then
      
      # If the command fails, print a clear error message to standard error and exit.
      echo "❌ ERROR: Failed to grant permissions for resource '$resource_desc' to principal '$principal_arn'." >&2
      exit 1
    fi
      
  done < <(jq -c '.[]' <<< "$desired_permissions")
done

echo "---"
echo "✅ Script finished successfully."
