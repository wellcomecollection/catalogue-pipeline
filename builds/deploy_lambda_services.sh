#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

PIPELINE_NAMESPACE="catalogue-$PIPELINE_DATE"
REPOSITORY_URI="760097843905.dkr.ecr.eu-west-1.amazonaws.com"

for FUNCTION_SUFFIX_IMAGE_NAME_PAIR in "$@"
do
  FUNCTION_SUFFIX="${FUNCTION_SUFFIX_IMAGE_NAME_PAIR%:*}"
  IMAGE_NAME="${FUNCTION_SUFFIX_IMAGE_NAME_PAIR#*:}"

  IMAGE_URI="${REPOSITORY_URI}"/uk.ac.wellcome/"${IMAGE_NAME}":"env.${PIPELINE_DATE}"
  FUNCTION_NAME="${PIPELINE_NAMESPACE}"-"${FUNCTION_SUFFIX}"

  echo "Deploying ${IMAGE_URI} to ${FUNCTION_NAME}, @ $(date) ..."

  echo "Current lambda configuration for ${FUNCTION_NAME}:"
  aws lambda get-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --no-cli-pager

  echo "Updating lambda configuration ..."
  echo "Using ${IMAGE_URI}:"
  aws lambda update-function-code \
    --function-name "$FUNCTION_NAME" \
    --image-uri "${IMAGE_URI}" \
    --no-cli-pager


  echo "Updated lambda configuration, (waiting for update @ $(date)}):"
  aws lambda wait function-updated \
    --function-name "$FUNCTION_NAME" \
    --no-cli-pager

  echo "New lambda configuration complete (@ $(date)), config after change:"
  aws lambda get-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --no-cli-pager

  echo "Done deploying ${FUNCTION_NAME} @ $(date)! 🚀"
done
