#!/usr/bin/env bash

# Usage: ./run_local.sh <args>

AWS_PROFILE=platform-developer

export AWS_PROFILE

FTP_PASSWORD=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/ftp_password --with-decryption --query "Parameter.Value" --output text)
FTP_SERVER=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/ftp_server --query "Parameter.Value" --output text)
FTP_USERNAME=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/ftp_username --query "Parameter.Value" --output text)
CUSTOMER_ID=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/customer_id --query "Parameter.Value" --output text)
FTP_REMOTE_DIR=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/ftp_remote_dir --query "Parameter.Value" --output text)
S3_BUCKET=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/bucket_name --query "Parameter.Value" --output text)
OUTPUT_TOPIC_ARN=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/output_topic_arn --query "Parameter.Value" --output text)
REINDEX_TOPIC_ARN=$(aws ssm get-parameter --name /catalogue_pipeline/ebsco_adapter/reindex_topic_arn --query "Parameter.Value" --output text)

# Update the S3_PREFIX to be the environment (use dev for local testing)
S3_PREFIX=prod

export FTP_PASSWORD FTP_SERVER FTP_USERNAME CUSTOMER_ID FTP_REMOTE_DIR S3_BUCKET S3_PREFIX OUTPUT_TOPIC_ARN REINDEX_TOPIC_ARN

# Ensure the docker image is up to date
docker compose build dev

docker compose run dev "$@"
