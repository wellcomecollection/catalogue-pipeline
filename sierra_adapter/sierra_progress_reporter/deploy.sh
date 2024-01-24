#!/usr/bin/env bash
# This builds a new version of the Sierra progress reporter Lambda,
# uploads it to S3, and updates the version running in the Lambda environment.

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)
S3_BUCKET="wellcomecollection-platform-infra"
S3_KEY="lambdas/sierra_adapter/sierra_progress_reporter.zip"
FUNCTION_NAME="sierra-adapter-20200604-sierra_progress_reporter"

echo "Building sierra_progress_reporter.zip"
pushd "$ROOT/sierra_adapter/sierra_progress_reporter"
  pip3 install \
    --target . \
    --platform manylinux2014_x86_64 \
    --only-binary=:all: \
    -r requirements.txt

  zip -r ../sierra_progress_reporter.zip ./*

  echo "Uploading to s3://$S3_BUCKET/$S3_KEY"
  aws s3 cp ../sierra_progress_reporter.zip "s3://$S3_BUCKET/$S3_KEY"
popd

echo "Updating $FUNCTION_NAME"
aws lambda update-function-code \
  --function-name "$FUNCTION_NAME" \
  --s3-bucket "$S3_BUCKET" \
  --s3-key "$S3_KEY"
