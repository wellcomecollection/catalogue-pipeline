#!/usr/bin/env bash

set -o errexit
set -o nounset

# The key is under admin/, which only platform-admin can read
AWS_CLI_PROFILE="catalogue-pipeline-terraform-admin"
PLATFORM_ADMIN_ARN="arn:aws:iam::760097843905:role/platform-admin"

aws configure set region eu-west-1 --profile $AWS_CLI_PROFILE
aws configure set role_arn $PLATFORM_ADMIN_ARN --profile $AWS_CLI_PROFILE
aws configure set source_profile default --profile $AWS_CLI_PROFILE

aws secretsmanager get-secret-value --secret-id admin/elastic_cloud/terraform_catalogue --profile "$AWS_CLI_PROFILE" --output text --query 'SecretString'
