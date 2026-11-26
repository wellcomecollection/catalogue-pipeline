#!/usr/bin/env bash
<<EOF
Publish a Docker image to ECR.

This is meant for use with sbt-based images -- we use Docker Compose for
our Node apps.  The image will be published both with the given tag and
the floating tag 'latest'.

This script is mirrored in our other Scala repos.

== Usage ==

Pass the name of the sbt project as arg 1, and the image tag as arg 2, e.g.

    $ publish_sbt_image_to_ecr.sh file_indexer ref.19872ab
    $ publish_sbt_image_to_ecr.sh snapshot_generator ref.1761817
    $ publish_sbt_image_to_ecr.sh transformer_mets ref.9811987

EOF

set -o errexit
set -o nounset

ECR_REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome"

if (( $# == 2))
then
  PROJECT_NAME="$1"
  IMAGE_TAG="$2"
else
  echo "Usage: publish_sbt_image_to_ecr.sh <PROJECT> <IMAGE_TAG>" >&2
  exit 1
fi

echo "*** Publishing Docker image to ECR"
# Login to ECR in the platform account is handled by the Buildkite runner

# An image with the same content is already in ECR: tag it rather than push a new digest for Inspector to scan
if ./builds/content_key.sh reuse "$PROJECT_NAME" "$IMAGE_TAG"
then
  exit 0
fi

docker tag "$PROJECT_NAME:$IMAGE_TAG" "$ECR_REGISTRY/$PROJECT_NAME:$IMAGE_TAG"
docker push "$ECR_REGISTRY/$PROJECT_NAME:$IMAGE_TAG"

docker tag "$PROJECT_NAME:$IMAGE_TAG" "$ECR_REGISTRY/$PROJECT_NAME:latest"
docker push "$ECR_REGISTRY/$PROJECT_NAME:latest"

# Tags the pushed image content.<key> for later builds to find
./builds/content_key.sh tag "$PROJECT_NAME" "$IMAGE_TAG"
