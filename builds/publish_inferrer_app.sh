#!/usr/bin/env bash
<<EOF
Publish a Docker image to ECR.

This is meant for use with the inferrer apps -- we use Docker Compose for
our Node apps.  The image will be published both with the given tag and
the floating tag 'latest'.

== Usage ==

Pass the name of the app as arg 1, and the image tag as arg 2, e.g.

    $ publish_inferrer_app.sh aspect_ratio_inferrer 19872ab
    $ publish_inferrer_app.sh feature_inferrer 1761817

EOF

set -o errexit
set -o nounset
set -o verbose

ECR_REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com"

if (( $# == 2))
then
  PROJECT_NAME="$1"
  IMAGE_TAG="$2"
else
  echo "Usage: publish_inferrer_app.sh <PROJECT> <IMAGE_TAG>" >&2
  exit 1
fi

ROOT=$(git rev-parse --show-toplevel)

docker build \
  --file "$ROOT/pipeline/inferrer/$PROJECT_NAME/Dockerfile" \
  --tag "$PROJECT_NAME:$IMAGE_TAG" \
  "$ROOT/pipeline/inferrer/$PROJECT_NAME"

echo "*** Publishing Docker image to ECR"

eval $(aws ecr get-login --no-include-email)

docker tag "$PROJECT_NAME:$IMAGE_TAG" "$ECR_REGISTRY/$PROJECT_NAME:$IMAGE_TAG"
docker push "$ECR_REGISTRY/$PROJECT_NAME:$IMAGE_TAG"

docker tag "$PROJECT_NAME:$IMAGE_TAG" "$ECR_REGISTRY/$PROJECT_NAME:latest"
docker push "$ECR_REGISTRY/$PROJECT_NAME:latest"
