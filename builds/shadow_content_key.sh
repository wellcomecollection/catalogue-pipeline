#!/usr/bin/env bash
<<EOF_USAGE
Publishing by content (wellcomecollection/catalogue-pipeline#3858).
Never fails the build: anything it cannot do falls back to pushing the image as before.

After a build:  shadow_content_key.sh check PROJECT PROJECT_DIRECTORY
    Works out the content key of the image just built and logs it.

Before a publish: shadow_content_key.sh reuse PROJECT IMAGE_TAG
    If an image with the same content is in ECR, tags it IMAGE_TAG, and latest
    while the branch head is still IMAGE_TAG, and exits 0 so the caller skips the push.
    Exits 1, with the reason logged, if the caller should push.
    PUBLISH_FORCE_PUSH=true always pushes, for getting an OS fix out the same day.

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
  echo "content-key: $BRANCH $PROJECT $KEY"
elif [[ "$MODE" == "reuse" ]]
then
  IMAGE_TAG="$3"
  if [[ "${PUBLISH_FORCE_PUSH:-}" == "true" ]]
  then
    echo "content-key: $BRANCH $PROJECT PUBLISH_FORCE_PUSH is set, pushing"
    exit 1
  fi
  if [[ ! -f "$KEY_FILE" ]]
  then
    echo "content-key: $BRANCH $PROJECT has no key from the build step, pushing"
    exit 1
  fi
  KEY=$(cat "$KEY_FILE")
  # Only a missing image is a miss; any other error is logged, so a broken lookup can't pass for misses
  if ! OUT=$(aws ecr batch-get-image \
      --repository-name "$ECR_REPOSITORY" \
      --image-ids imageTag="content.$KEY" --output json 2>&1)
  then
    echo "content-key: $BRANCH $PROJECT $KEY lookup failed, pushing: $OUT"
    exit 1
  fi
  MANIFEST=$(jq --raw-output --join-output '.images[0].imageManifest' <<< "$OUT")
  # Stored with the type it was read with, so put-image keeps the same digest
  MEDIA_TYPE=$(jq --raw-output '.images[0].imageManifestMediaType // empty' <<< "$OUT")
  if [[ -z "$MANIFEST" || "$MANIFEST" == "null" ]]
  then
    echo "content-key: $BRANCH $PROJECT $KEY no image with this content, pushing"
    exit 1
  fi
  TAGS=("$IMAGE_TAG")
  LS_REMOTE=$(git ls-remote origin "refs/heads/$BRANCH" 2>/dev/null) || LS_REMOTE=""
  HEAD=$(cut -f1 <<< "$LS_REMOTE")
  if [[ -z "$HEAD" ]]
  then
    echo "content-key: $BRANCH $PROJECT could not read the head of $BRANCH, pushing"
    exit 1
  fi
  # An older commit must not move latest back after a newer build has set it
  if [[ "$HEAD" == "$IMAGE_TAG" ]]
  then
    TAGS+=(latest)
  else
    echo "content-key: $BRANCH $PROJECT $BRANCH has moved past $IMAGE_TAG, leaving latest"
  fi
  # The commit tag first: if latest fails after it, the caller's push moves both tags to the new image
  for TAG in "${TAGS[@]}"
  do
    if ! OUT=$(aws ecr put-image \
        --repository-name "$ECR_REPOSITORY" \
        --image-tag "$TAG" \
        --image-manifest "$MANIFEST" \
        ${MEDIA_TYPE:+--image-manifest-media-type "$MEDIA_TYPE"} 2>&1) && ! grep -q ImageAlreadyExistsException <<< "$OUT"
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
