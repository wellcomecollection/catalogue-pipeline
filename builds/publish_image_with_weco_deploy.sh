#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o verbose

ECR_REGISTRY="760097843905.dkr.ecr.eu-west-1.amazonaws.com"
WECO_DEPLOY_IMAGE="wellcome/weco-deploy:5.6.11"

ROOT=$(git rev-parse --show-toplevel)

IMAGE_ID="$1"

# TODO: This is working out the weco-project project ID, which we could
# derive programatically from .wellcome_project.
case "$IMAGE_ID" in
  "mets_adapter")
    PROJECT_ID="mets_adapter"
    ;;

  "calm_adapter" | "calm_deletion_checker" | "calm_indexer")
    PROJECT_ID="calm_adapter"
    ;;

  "tei_id_extractor" | "tei_adapter")
    PROJECT_ID="tei_adapter"
    ;;

  "sierra_merger" | "sierra_linker" | "sierra_reader" | "sierra_indexer")
    PROJECT_ID="sierra_adapter"
    ;;

  "reindex_worker")
    PROJECT_ID="reindexer"
    ;;

  "id_minter" | "inference_manager" | "feature_inferrer" | "palette_inferrer" | "aspect_ratio_inferrer" | "matcher" | "merger" | "ingestor_images" | "ingestor_works" | "router" | "batcher" | "relation_embedder" | "transformer_calm" | "transformer_mets" | "transformer_miro" | "transformer_sierra" | "transformer_tei")
    PROJECT_ID="catalogue_pipeline"
    ;;
esac

docker run --tty --rm \
  --env AWS_PROFILE \
  --volume ~/.aws:/root/.aws \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --volume "$DOCKER_CONFIG:/root/.docker" \
  --volume "$ROOT:$ROOT" \
  --workdir "$ROOT" \
  "$ECR_REGISTRY/$WECO_DEPLOY_IMAGE" \
    --project-id="$PROJECT_ID" \
    --verbose \
    publish \
    --image-id="$IMAGE_ID"
