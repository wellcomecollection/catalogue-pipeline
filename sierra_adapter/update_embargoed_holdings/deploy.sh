#!/usr/bin/env bash
# This builds a new version of the Lambda, uploads it to S3,
# and updates the version running in the Lambda environment.

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)

pushd "$ROOT/sierra_adapter/update_embargoed_holdings"
  pip3 install \
    --target . \
    --platform manylinux2014_x86_64 \
    --only-binary=:all: \
    -r requirements.txt
  zip -r ../update_embargoed_holdings.zip *
popd

S3_BUCKET="wellcomecollection-platform-infra"
S3_KEY="lambdas/sierra_adapter/update_embargoed_holdings.zip"

aws s3 cp update_embargoed_holdings.zip "s3://$S3_BUCKET/$S3_KEY"

for resource in bibs items orders holdings
do
  aws lambda update-function-code \
    --function-name "update_embargoed_holdings" \
    --s3-bucket "$S3_BUCKET" \
    --s3-key "$S3_KEY"
done
