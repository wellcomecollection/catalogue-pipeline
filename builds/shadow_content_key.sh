#!/usr/bin/env bash
<<EOF_USAGE
Shadow mode for publishing by content (wellcomecollection/catalogue-pipeline#3858).
Changes nothing about what is built, published or deployed, and never fails the build.

After a build:  shadow_content_key.sh PROJECT
    Works out the content key of the image just built and logs whether an
    image with the same content is already in ECR.

After a publish: shadow_content_key.sh PROJECT IMAGE_TAG
    Also tags the image just pushed as content.<key>, so later builds can find it.

Log lines start with "content-key:" so they can be collected from build logs.
EOF_USAGE

set -o nounset

ECR_REPOSITORY="uk.ac.wellcome/$1"
ROOT=$(git rev-parse --show-toplevel)
PROJECT_DIRECTORY=$("$ROOT/builds/get_sbt_project_directory.sh" "$1")
# Not under target/, which sbt writes as root inside Docker
KEY_FILE="$ROOT/.content_key.$1"

if (( $# == 1 ))
then
  if ! KEY=$(python3 "$ROOT/builds/image_content_key.py" "$PROJECT_DIRECTORY" "$ROOT")
  then
    echo "content-key: $1 could not be worked out"
    exit 0
  fi
  echo "$KEY" > "$KEY_FILE"

  EXISTING=$(aws ecr describe-images \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="content.$KEY" \
    --query 'imageDetails[0].imageDigest' --output text 2>/dev/null) || EXISTING=""

  if [[ -n "$EXISTING" && "$EXISTING" != "None" ]]
  then
    echo "content-key: $1 $KEY would reuse $EXISTING"
  else
    echo "content-key: $1 $KEY would push a new image"
  fi
elif (( $# == 2 ))
then
  if [[ ! -f "$KEY_FILE" ]]
  then
    echo "content-key: $1 has no key from the build step, not tagging"
    exit 0
  fi
  KEY=$(cat "$KEY_FILE")
  # The same way update_ecr_image_tag.sh reads it, so put-image keeps the digest
  MANIFEST=$(aws ecr batch-get-image \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="$2" --output json 2>/dev/null \
    | jq --raw-output --join-output '.images[0].imageManifest') || MANIFEST=""
  if [[ -z "$MANIFEST" || "$MANIFEST" == "null" ]]
  then
    echo "content-key: $1 could not read the manifest for $2, not tagging"
    exit 0
  fi
  if OUT=$(aws ecr put-image \
      --repository-name "$ECR_REPOSITORY" \
      --image-tag "content.$KEY" \
      --image-manifest "$MANIFEST" 2>&1) || grep -q ImageAlreadyExistsException <<< "$OUT"
  then
    echo "content-key: $1 $KEY tagged on $2"
  else
    echo "content-key: $1 could not tag content.$KEY: $OUT"
  fi
else
  echo "Usage: shadow_content_key.sh <PROJECT> [<IMAGE_TAG>]" >&2
fi
exit 0
