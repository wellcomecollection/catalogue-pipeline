#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail


SERVICE_NAME=$1

FUNCTION_NAME="${SERVICE_NAME}"
REPOSITORY_URI="760097843905.dkr.ecr.eu-west-1.amazonaws.com"

IMAGE_URI="${REPOSITORY_URI}/uk.ac.wellcome/${SERVICE_NAME}:dev"

echo "Deploying ${IMAGE_URI} to ${SERVICE_NAME}, @ $(date) ..."

echo "Current lambda configuration for ${SERVICE_NAME}:"
aws lambda get-function-configuration \
  --function-name "$SERVICE_NAME" \
  --no-cli-pager

echo "Updating lambda configuration ..."
echo "Using ${IMAGE_URI}:"
aws lambda update-function-code \
  --function-name "$SERVICE_NAME" \
  --image-uri "${IMAGE_URI}" \
  --publish \
  --no-cli-pager 

echo "Updated lambda configuration, (waiting for update @ $(date)}):"
aws lambda wait function-updated \
  --function-name "$SERVICE_NAME" \
  --no-cli-pager

echo "New lambda configuration complete (@ $(date)), config after change:"
aws lambda get-function-configuration \
  --function-name "$SERVICE_NAME" \
  --no-cli-pager

echo "Done deploying ${SERVICE_NAME} @ $(date)! 🚀"
done