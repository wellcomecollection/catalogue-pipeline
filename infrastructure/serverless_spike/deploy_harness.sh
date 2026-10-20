#!/usr/bin/env bash
# Build the harness image on the CI-built base and point the spike's Lambda at it.
# Usage: ./deploy_harness.sh [FUNCTION_NAME]

set -euo pipefail

FUNCTION_NAME="${1:-catalogue-2026-10-14-serverless-es-bench}"
REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com"
IMAGE="$REGISTRY/uk.ac.wellcome/unified_pipeline_lambda:dev-es-bench"
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"

aws ecr get-login-password --profile platform-developer \
  | docker login --username AWS --password-stdin "$REGISTRY"

docker pull --platform linux/amd64 "$REGISTRY/uk.ac.wellcome/unified_pipeline_lambda:latest"

# Lambda rejects buildx attestation manifests, hence no provenance or SBOM.
docker buildx build --platform linux/amd64 --provenance=false --sbom=false \
  -f "$ROOT/infrastructure/serverless_spike/harness.Dockerfile" \
  -t "$IMAGE" --push "$ROOT"

aws lambda update-function-code --profile platform-developer \
  --function-name "$FUNCTION_NAME" --image-uri "$IMAGE" --publish --no-cli-pager \
  --query '[FunctionName,LastUpdateStatus,CodeSha256]' --output text

aws lambda wait function-updated --profile platform-developer --function-name "$FUNCTION_NAME"
echo "deployed $IMAGE to $FUNCTION_NAME"
