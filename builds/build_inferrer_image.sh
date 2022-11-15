#!/usr/bin/env bash
<<EOF
Build the Docker image for an inferrer project.

This is meant for use with the inferrer images -- these are slightly different
to our Scala apps.

== Usage ==

Pass the name of the project as arg 1, and the image tag as arg 2, e.g.

    $ build_inferrer_image.sh aspect_ratio_inferrere ref.19872ab
    $ build_inferrer_image.sh palette_inferrer ref.1761817

EOF

set -o errexit
set -o nounset

if (( $# == 2))
then
  PROJECT_NAME="$1"
  IMAGE_TAG="$2"
else
  echo "Usage: build_inferrer_image.sh <PROJECT> <IMAGE_TAG>" >&2
  exit 1
fi

docker build \
  --file "pipeline/inferrer/$PROJECT_NAME/Dockerfile" \
  --tag "$PROJECT_NAME:$IMAGE_TAG" \
  "pipeline/inferrer/$PROJECT_NAME"
