#!/usr/bin/env bash

set -o errexit
set -o nounset

AWS_CLI_PROFILE="catalogue-pipeline-terraform"
PLATFORM_DEVELOPER_ARN="arn:aws:iam::760097843905:role/platform-developer"

aws configure set region eu-west-1 --profile $AWS_CLI_PROFILE
aws configure set role_arn $PLATFORM_DEVELOPER_ARN --profile $AWS_CLI_PROFILE
aws configure set source_profile default --profile $AWS_CLI_PROFILE

aws secretsmanager get-secret-value --secret-id elastic_cloud/api_key --profile "$AWS_CLI_PROFILE" --output text --query 'SecretString'
