#!/usr/bin/env bash

set -o errexit
set -o nounset

# set ROOT to the root of the project
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT+="$(dirname "$DIR")"

# get python version from .python-version
PY_VERSION=$(cat catalogue_graph/.python-version)

# Install UV if not available
if ! command -v uv &> /dev/null; then
    echo "Installing UV..."
    pip install uv
fi

# set default values
ECR_REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome"
S3_BUCKET="wellcomecollection-platform-infra"
S3_PREFIX="lambdas/catalogue_graph"

ZIP_TARGET="${ROOT}/target/build.zip"
TAG_DEFAULT="dev"
PUSH=false
SKIP_CONTAINER_BUILD=false

# parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--tag)
      TAG=${2:-$TAG_DEFAULT}
      echo "Using tag: $TAG"
      shift 
      ;;
    -p|--push)
      PUSH=true
      echo "Will push build artifacts to AWS"
      ;;
    -s|--skip-container-build)
      SKIP_CONTAINER_BUILD=true
      echo "Will skip building the container"
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
  # dont fail if at end of arguments
  shift || true 
done

# change working directory to the root of the project
cd "$ROOT"

function build_zip() {( set -e
    local ZIP_TARGET=$1
    # Ensure the target directory is clean
    rm -rf target/tmp
    rm -f $ZIP_TARGET

    mkdir -p target/tmp

    cp -r src/* target/tmp
    
    # Use UV to install dependencies directly from pyproject.toml
    uv pip install \
        -r <(uv export --no-dev --format requirements-txt) \
        --python-platform x86_64-manylinux2014 \
        --target target/tmp \
        --only-binary=:all: \
        --no-deps

    pushd target/tmp
    zip -r $ZIP_TARGET .
    popd

    rm -rf target/tmp
)}

function upload_zip {( set -e
    local FILE=$1
    local TAG=${TAG:-$TAG_DEFAULT}

    DESTINATION="s3://$S3_BUCKET/$S3_PREFIX/lambda-$TAG.zip"

    aws s3 cp $FILE $DESTINATION
)}

function docker_compose {( set -e
    local CMD=$1
    local SERVICE_NAME=$2

    TAG=${TAG:-$TAG_DEFAULT} \
    REPOSITORY_PREFIX=${ECR_REGISTRY}/ \
    PYTHON_IMAGE_VERSION=${PY_VERSION}-slim \
    docker compose $CMD $SERVICE_NAME
)}

build_zip "$ZIP_TARGET"

if [ "$SKIP_CONTAINER_BUILD" == false ]; then
    docker_compose "build" "extractor"
fi

if [ "$PUSH" == true ]; then
    upload_zip "$ZIP_TARGET" 
    if [ "$SKIP_CONTAINER_BUILD" == false ]; then
        docker_compose "push" "extractor"
    fi
fi
