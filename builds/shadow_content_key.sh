#!/usr/bin/env bash
<<EOF_USAGE
Publishing by content (wellcomecollection/catalogue-pipeline#3858).
Never fails the build: anything it cannot do falls back to pushing the image as before.

After a build:  shadow_content_key.sh check PROJECT PROJECT_DIRECTORY
    Works out the content key of the image just built and logs whether an
    image with the same content is already in ECR.

Before a publish: shadow_content_key.sh reuse PROJECT IMAGE_TAG
    If an image with the same content is in ECR, tags it IMAGE_TAG and latest
    and exits 0, so the caller skips the push. Exits 1 if the caller should push.

After a publish: shadow_content_key.sh tag PROJECT IMAGE_TAG
    Tags the image just pushed as content.<key>, so later builds can find it.

Log lines start with "content-key: BRANCH PROJECT" so they can be collected from build logs.
EOF_USAGE

set -o nounset

USAGE="Usage: shadow_content_key.sh check <PROJECT> <PROJECT_DIRECTORY> | reuse <PROJECT> <IMAGE_TAG> | tag <PROJECT> <IMAGE_TAG>"

if (( $# != 3 ))
then
  echo "$USAGE" >&2
  # A bad reuse call must not let the caller skip its push
  [[ "${1:-}" == "reuse" ]] && exit 1
  exit 0
fi

MODE="$1"
PROJECT="$2"
ECR_REPOSITORY="uk.ac.wellcome/$PROJECT"
ROOT=$(git rev-parse --show-toplevel)
# Not under target/, which sbt writes as root inside Docker
KEY_FILE="$ROOT/.content_key.$PROJECT"
# PR branches are checked too, so the count can be filtered to main
BRANCH="${BUILDKITE_BRANCH:-local}"

if [[ "$MODE" == "check" ]]
then
  # A reused checkout may hold the last build's key; a failed calculation must leave none for tag
  rm -f "$KEY_FILE"
  # The caller passes the directory it already resolved, which saves starting sbt again
  if ! KEY=$(python3 "$ROOT/builds/image_content_key.py" "$3" "$ROOT")
  then
    echo "content-key: $BRANCH $PROJECT could not be worked out"
    exit 0
  fi
  echo "$KEY" > "$KEY_FILE"

  # Only a missing image is a miss; any other error is logged as unknown so it can't skew the count
  if EXISTING=$(aws ecr describe-images \
      --repository-name "$ECR_REPOSITORY" \
      --image-ids imageTag="content.$KEY" \
      --query 'imageDetails[0].imageDigest' --output text 2>&1)
  then
    echo "content-key: $BRANCH $PROJECT $KEY would reuse $EXISTING"
  elif grep -q ImageNotFoundException <<< "$EXISTING"
  then
    echo "content-key: $BRANCH $PROJECT $KEY would push a new image"
  else
    echo "content-key: $BRANCH $PROJECT $KEY lookup failed: $EXISTING"
  fi
elif [[ "$MODE" == "reuse" ]]
then
  IMAGE_TAG="$3"
  [[ -f "$KEY_FILE" ]] || exit 1
  KEY=$(cat "$KEY_FILE")
  MANIFEST=$(aws ecr batch-get-image \
    --repository-name "$ECR_REPOSITORY" \
    --image-ids imageTag="content.$KEY" --output json 2>/dev/null \
    | jq --raw-output --join-output '.images[0].imageManifest') || MANIFEST=""
  [[ -n "$MANIFEST" && "$MANIFEST" != "null" ]] || exit 1
  # The commit tag first: if latest fails after it, the caller's push moves both tags to the new image
  for TAG in "$IMAGE_TAG" latest
  do
    if ! OUT=$(aws ecr put-image \
        --repository-name "$ECR_REPOSITORY" \
        --image-tag "$TAG" \
        --image-manifest "$MANIFEST" 2>&1) && ! grep -q ImageAlreadyExistsException <<< "$OUT"
    then
      echo "content-key: $BRANCH $PROJECT could not tag $TAG on content.$KEY, pushing instead: $OUT"
      exit 1
    fi
  done
  echo "content-key: $BRANCH $PROJECT $KEY reused for $IMAGE_TAG, nothing pushed"
  exit 0
elif [[ "$MODE" == "tag" ]]
then
  IMAGE_TAG="$3"
  if [[ ! -f "$KEY_FILE" ]]
  then
    echo "content-key: $BRANCH $PROJECT has no key from the build step, not tagging"
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
    echo "content-key: $BRANCH $PROJECT could not read the manifest for $IMAGE_TAG, not tagging"
    exit 0
  fi
  if OUT=$(aws ecr put-image \
      --repository-name "$ECR_REPOSITORY" \
      --image-tag "content.$KEY" \
      --image-manifest "$MANIFEST" 2>&1) || grep -q ImageAlreadyExistsException <<< "$OUT"
  then
    echo "content-key: $BRANCH $PROJECT $KEY tagged on $IMAGE_TAG"
  else
    echo "content-key: $BRANCH $PROJECT could not tag content.$KEY: $OUT"
  fi
else
  echo "$USAGE" >&2
fi
exit 0
