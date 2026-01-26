#!/usr/bin/env bash
<<EOF
Ensure there are Docker images tagged for a pipeline.

Each instance of the catalogue pipeline is tagged with a particular date
(e.g. 2022-11-09), and uses images with a corresponding tag (e.g. env.2022-11-09).
This means different pipelines can use different versions of our apps.

This script ensures that we have tagged images for a given pipeline --

-   if the tags already exist, they're left as-is
-   if the tags don't yet exist, they'll be created based on 'latest'

== Usage example ==

    $ PIPELINE_DATE=2022-11-09 ensure_ecr_tags_exist_for_pipeline.sh

EOF

set -o errexit
set -o nounset

ROOT=$(git rev-parse --show-toplevel)

ENV_TAG="env.$PIPELINE_DATE"

# This will return a JSON object with two keys: images and failures.
#
# The 'images' object will have a single entry if it finds an image with
# a matching tag; the 'failures' object will have a single entry if it
# doesn't.
ECR_RESPONSE=$(
  aws ecr batch-get-image \
    --repository-name "uk.ac.wellcome/batcher" \
    --image-ids imageTag="$ENV_TAG" --output json
)

EXISTING_IMAGES=$(echo "$ECR_RESPONSE" | jq '.images | length')

if (( EXISTING_IMAGES == 2 ))
then
  echo "There are already images with tag $ENV_TAG, nothing to do"
  exit 0
else
  echo "There are no images with tag $ENV_TAG, creating tags"
  $ROOT/builds/deploy_catalogue_pipeline.sh tag_images
fi
