#!/usr/bin/env bash
# This builds a new version of the Sierra reader Lambda, uploads it to S3,
# and updates the version running in the Lambda environment.

set -o errexit
set -o nounset

pushd sierra_reader
  pip3 install \
    --target . \
    --platform manylinux2014_x86_64 \
    --only-binary=:all: \
    -r requirements.txt
  zip -r ../sierra_reader.zip *
popd

S3_BUCKET="wellcomecollection-platform-infra"
S3_KEY="lambdas/sierra_adapter/sierra_reader.zip"

aws s3 cp sierra_reader.zip "s3://$S3_BUCKET/$S3_KEY"

for resource in bibs items orders holdings
do
  aws lambda update-function-code \
    --function-name "sierra-adapter-20200604-sierra-reader-$resource" \
    --s3-bucket "$S3_BUCKET" \
    --s3-key "$S3_KEY"
done
