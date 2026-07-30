#!/usr/bin/env bash
<<EOF
Refuse to move a tag onto a commit that already contains the one being
deployed, which happens when deploy runs finish out of order.

Images carry their commit as a tag, so we can read where the target tag points.
Writes skip_push=true|false to GITHUB_OUTPUT. Anything we cannot determine
deploys as before, so this only blocks a move it can prove is backwards.
EOF

set -o errexit
set -o nounset
set -o pipefail

REGISTRY="$1"
IMAGE_NAME="$2"
SOURCE_TAG="$3"
TARGET_TAG="$4"

# 123456789012.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome -> uk.ac.wellcome
NAMESPACE="${REGISTRY#*/}"
if [[ "$NAMESPACE" == "$REGISTRY" ]]
then
  REPOSITORY_NAME="$IMAGE_NAME"
else
  REPOSITORY_NAME="$NAMESPACE/$IMAGE_NAME"
fi

emit() {
  echo "skip_push=$1" >> "$GITHUB_OUTPUT"
  exit 0
}

if ! SOURCE_COMMIT=$(git rev-parse --verify --quiet "${SOURCE_TAG}^{commit}")
then
  echo "::warning::$SOURCE_TAG is not a commit here, deploying without the backwards check"
  emit false
fi

# Shallow clones answer every ancestry question with "no".
if [[ "$(git rev-parse --is-shallow-repository)" == "true" ]]
then
  echo "::warning::shallow checkout, so ancestry cannot be established. Deploying without the backwards check: set fetch-depth 0."
  emit false
fi

if ! CURRENT_TAGS=$(
  aws ecr describe-images \
    --repository-name "$REPOSITORY_NAME" \
    --image-ids imageTag="$TARGET_TAG" \
    --query 'imageDetails[0].imageTags' \
    --output text 2>&1
)
then
  if [[ "$CURRENT_TAGS" == *ImageNotFoundException* ]]
  then
    echo "$TARGET_TAG does not exist in $REPOSITORY_NAME yet, nothing to move backwards"
  else
    echo "::warning::could not read $TARGET_TAG from $REPOSITORY_NAME, deploying without the backwards check"
    echo "$CURRENT_TAGS"
  fi
  emit false
fi

if [[ -z "$CURRENT_TAGS" ]]
then
  echo "::warning::$TARGET_TAG in $REPOSITORY_NAME reported no tags, deploying without the backwards check"
  emit false
fi

TARGET_COMMIT=$(tr '[:space:]' '\n' <<< "$CURRENT_TAGS" | grep -E '^[0-9a-f]{40}$' | head -n 1 || true)

if [[ -z "$TARGET_COMMIT" ]]
then
  echo "::warning::$TARGET_TAG in $REPOSITORY_NAME has no commit tag, deploying without the backwards check"
  emit false
fi

# The tag may point at a merge that landed after ours.
git fetch --no-tags --quiet origin "$TARGET_COMMIT" 2>/dev/null || true

if ! git cat-file -e "${TARGET_COMMIT}^{commit}" 2>/dev/null
then
  echo "::warning::cannot fetch $TARGET_COMMIT behind $TARGET_TAG, deploying without the backwards check"
  emit false
fi

if [[ "$SOURCE_COMMIT" == "$TARGET_COMMIT" ]]
then
  echo "$TARGET_TAG in $REPOSITORY_NAME is already on $SOURCE_COMMIT"
  emit true
fi

if git merge-base --is-ancestor "$SOURCE_COMMIT" "$TARGET_COMMIT"
then
  echo "::warning::$TARGET_TAG in $REPOSITORY_NAME points at $TARGET_COMMIT, which already contains $SOURCE_COMMIT. Not moving it backwards."
  emit true
fi

echo "$TARGET_TAG in $REPOSITORY_NAME points at $TARGET_COMMIT, which does not contain $SOURCE_COMMIT"
emit false
