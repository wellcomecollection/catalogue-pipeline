#!/bin/bash

set -euo pipefail

echo "🔐 Assuming AWS role..."

aws_creds=$(aws sts assume-role \
    --role-arn "arn:aws:iam::975596993436:role/storage-read_only" \
    --role-session-name "local-mets-transformer-session" \
    --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
    --output text)

read -r AWS_ACCESS_KEY_ID_STORAGE AWS_SECRET_ACCESS_KEY_STORAGE AWS_SESSION_TOKEN_STORAGE <<< "$aws_creds"

echo "✅ AWS roles assumed successfully"

echo "Setting up LocalStack resources: queue and topic..."

# Create queue and topic
echo "Creating SQS queue and SNS topic..."
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name mets-transformer-queue --region eu-west-1 2>/dev/null || echo "Queue already exists"
aws --endpoint-url=http://localhost:4566 sns create-topic --name mets-transformer-topic --region eu-west-1 2>/dev/null || echo "Topic already exists"

echo "�🚀 Starting METS transformer..."

cd ../../../..

echo "Building transformer..."
sbt "transformer_mets/compile"

echo "Running transformer..."
export es_host="localhost"
export es_port="9200"
export es_protocol="http" 
export es_index="works-source-dev"
export es_apikey="dummy-api-key"
export queue_url="http://localhost:4566/000000000000/mets-transformer-queue"
export sns_topic_arn="arn:aws:sns:eu-west-1:000000000000:mets-transformer-topic"
export metrics_namespace="mets-transformer"
export batch_size="100"
export flush_interval_seconds="30"

# Configure AWS SDK to use LocalStack endpoints for SQS/SNS only
# S3 will use real AWS with the assumed role credentials
export AWS_ENDPOINT_URL_SQS="http://localhost:4566"
export AWS_ENDPOINT_URL_SNS="http://localhost:4566"

# Use storage credentials for S3 access
export AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID_STORAGE"
export AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY_STORAGE" 
export AWS_SESSION_TOKEN="$AWS_SESSION_TOKEN_STORAGE"
export AWS_DEFAULT_REGION="eu-west-1"

sbt "transformer_mets/run" 