#!/bin/bash

set -e
set -o pipefail
set -u # treat unset variables as an error

# Helper script for devs to build and deploy the Lambda Docker image for testing
# Usage: ./deploy_lambda.sh <LAMBDA_NAME>

LAMBDA_NAME=$1

# Set dir as parent dir of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/.. >/dev/null 2>&1 && pwd )"
cd $DIR

# Docker login public
aws ecr-public get-login-password \
--region us-east-1 \
--profile platform-developer | docker login \
--username AWS \
--password-stdin public.ecr.aws

# Docker login ECR private (to push built image)
aws ecr get-login-password \
--profile platform-developer | docker login \
--username AWS \
--password-stdin 760097843905.dkr.ecr.eu-west-1.amazonaws.com

# From ./catalogue_graph/ build and tag the image
docker buildx build --platform linux/amd64 \
--provenance=false \
-t 760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/unified_pipeline_lambda:dev \
--build-arg PYTHON_IMAGE_VERSION=$(cat .python-version) \
-f lambda.Dockerfile .

# Push the built image to ECR
docker push 760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/unified_pipeline_lambda:dev

# Update a particular Lambda function to use the new image
aws lambda update-function-code \
--function-name $LAMBDA_NAME \
--image-uri 760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/unified_pipeline_lambda:dev \
--profile platform-developer \
--publish
