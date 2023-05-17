#!/usr/bin/env bash

set -o errexit
set -o nounset

pushd sierra_reader_new
  pip3 install --target . --platform manylinux2014_x86_64 --only-binary=:all: -r requirements.txt
  zip -r ../sierra_reader.zip *
popd

AWS_PROFILE=platform-dev aws s3 cp sierra_reader.zip s3://wellcomecollection-platform-infra/lambdas/sierra_adapter/sierra_reader.zip

AWS_PROFILE=platform-dev aws lambda update-function-code --function-name sierra-adapter-20200604-sierra-reader-bibs --s3-bucket wellcomecollection-platform-infra  --s3-key lambdas/sierra_adapter/sierra_reader.zip

AWS_PROFILE=platform-dev aws lambda update-function-code --function-name sierra-adapter-20200604-sierra-reader-orders --s3-bucket wellcomecollection-platform-infra  --s3-key lambdas/sierra_adapter/sierra_reader.zip

AWS_PROFILE=platform-dev aws lambda update-function-code --function-name sierra-adapter-20200604-sierra-reader-holdings --s3-bucket wellcomecollection-platform-infra  --s3-key lambdas/sierra_adapter/sierra_reader.zip