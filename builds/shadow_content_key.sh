#!/usr/bin/env bash
<<EOF_USAGE
Shadow mode for publishing by content (wellcomecollection/catalogue-pipeline#3858).
Changes nothing about what is built, published or deployed, and never fails the build.

After a build:  shadow_content_key.sh check PROJECT PROJECT_DIRECTORY
    Works out the content key of the image just built and logs whether an
    image with the same content is already in ECR.

After a publish: shadow_content_key.sh tag PROJECT IMAGE_TAG
    Tags the image just pushed as content.<key>, so later builds can find it.

Log lines start with "content-key:" so they can be collected from build logs.
EOF_USAGE

set -o nounset

if (( $# != 3 ))
then
  echo "Usage: shadow_content_key.sh check <PROJECT> <PROJECT_DIRECTORY> | tag <PROJECT> <IMAGE_TAG>" >&2
  exit 0
fi

MODE="$1"
PROJECT="$2"
ECR_REPOSITORY="uk.ac.wellcome/$PROJECT"
ROOT=$(git rev-parse --show-toplevel)
# Not under target/, which sbt writes as root inside Docker
KEY_FILE="$ROOT/.content_key.$PROJECT"

if [[ "$MODE" == "check" ]]
then
  # The caller passes the directory it already resolved, which saves starting sbt again
  if ! KEY=$(python3 "$ROOT/builds/image_content_key.py" "$3" "$ROOT")
  then
    echo "content-key: $PROJECT could not be worked out"
    exit 0
  fi
  echo "$KEY" > "$KEY_FILE"

  # Only a missing image is a miss; any other error is logged as unknown so it can't skew the count
  if EXISTING=$(aws ecr describe-images \
      --repository-name "$ECR_REPOSITORY" \
      --image-ids imageTag="content.$KEY" \
      --query 'imageDetails[0].imageDigest' --output text 2>&1)
  then
    echo "content-key: $PROJECT $KEY would reuse $EXISTING"
  elif grep -q ImageNotFoundException <<< "$EXISTING"
  then
    echo "content-key: $PROJECT $KEY would push a new image"
  else
    echo "content-key: $PROJECT $KEY lookup failed: $EXISTING"
  fi
elif [[ "$MODE" == "tag" ]]
then
  IMAGE_TAG="$3"
  if [[ ! -f "$KEY_FILE" ]]
  then
    echo "content-key: $PROJECT has no key from the build step, not tagging"
    exit 0
  fi
  KEY=$(cat "$KEY_FILE")
  # The same way update_ecr_image_tag.sh reads it, so put-image keeps the digest
  MANIFEST=$(aws ecr batch-get-image \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$IMAGE_TAG" --output json 2>/dev/null \
    | jq --raw-output --join-output '.images[0].imageManifest') || MANIFEST=""
  if [[ -z "$MANIFEST" || "$MANIFEST" == "null" ]]
  then
    echo "content-key: $PROJECT could not read the manifest for $IMAGE_TAG, not tagging"
    exit 0
  fi
  if OUT=$(aws ecr put-image \
      --repository-name "$ECR_REPOSITORY" \
      --image-tag "content.$KEY" \
      --image-manifest "$MANIFEST" 2>&1) || grep -q ImageAlreadyExistsException <<< "$OUT"
  then
    echo "content-key: $PROJECT $KEY tagged on $IMAGE_TAG"
  else
    echo "content-key: $PROJECT could not tag content.$KEY: $OUT"
  fi
else
  echo "Usage: shadow_content_key.sh check <PROJECT> <PROJECT_DIRECTORY> | tag <PROJECT> <IMAGE_TAG>" >&2
fi
exit 0
