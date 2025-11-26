#!/bin/bash

echo "📮 Creating SQS queue..."
awslocal sqs create-queue --queue-name mets-transformer-queue --region eu-west-1

echo "📢 Creating SNS topic..."
awslocal sns create-topic --name mets-transformer-topic --region eu-west-1

echo "✅ LocalStack resources created successfully!"
