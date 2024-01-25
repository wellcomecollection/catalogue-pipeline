#!/usr/bin/env bash
# This builds a new version of the Sierra reader Lambda, uploads it to S3,
# and updates the version running in the Lambda environment.

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)
S3_BUCKET="wellcomecollection-platform-infra"
S3_KEY="lambdas/sierra_adapter/sierra_reader.zip"
FUNCTION_NAME_BASE="sierra-adapter-20200604-sierra-reader"

echo "Building sierra_reader.zip"
pushd "$ROOT/sierra_adapter/sierra_reader"
  pip3 install \
    --target . \
    --platform manylinux2014_x86_64 \
    --only-binary=:all: \
    -r requirements.txt

  zip -r ../sierra_reader.zip ./*

  echo "Uploading to s3://$S3_BUCKET/$S3_KEY"
  aws s3 cp ../sierra_reader.zip "s3://$S3_BUCKET/$S3_KEY"
popd

for RESOURCE in bibs items orders holdings
do
  echo "Updating $FUNCTION_NAME_BASE-$RESOURCE"
  aws lambda update-function-code \
    --function-name "$FUNCTION_NAME_BASE-$RESOURCE" \
    --s3-bucket "$S3_BUCKET" \
    --s3-key "$S3_KEY"
done
