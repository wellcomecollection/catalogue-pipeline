#!/usr/bin/env bash

set -o errexit
set -o nounset

ECR_REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com"

ROOT=$(git rev-parse --show-toplevel)

docker run --tty --rm \
	--volume "$ROOT:/repo" \
  --workdir /repo \
	"$ECR_REGISTRY/pyfound/black" \
  black --exclude ".lambda_zips/|.terraform/|target/" .
