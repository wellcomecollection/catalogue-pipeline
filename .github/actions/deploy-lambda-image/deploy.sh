#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

LAMBDA_NAME="$1"
REGISTRY="$2"
IMAGE_NAME="$3"
DEPLOY_TAG="$4"

IMAGE_URI="${REGISTRY}/${IMAGE_NAME}:${DEPLOY_TAG}"

echo "Identifying function: ${LAMBDA_NAME}" 
FUNCTION_ARN=$(aws lambda get-function-configuration \
  --function-name "${LAMBDA_NAME}" \
  --query 'FunctionArn' \
  --output text)

echo "Updating function ${FUNCTION_ARN} to image ${IMAGE_URI}" 
REVISION_ID=$(aws lambda update-function-code \
  --function-name "${LAMBDA_NAME}" \
  --image-uri "${IMAGE_URI}" \
  --publish \
  --query 'RevisionId' \
  --output text)

echo "Revision id: ${REVISION_ID}"

echo "Awaiting function update" 
aws lambda wait function-updated --function-name "${LAMBDA_NAME}"
echo "Done" 
