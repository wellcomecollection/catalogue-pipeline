#!/usr/bin/env bash
<<EOF
Trigger a deployment of the catalogue pipeline.

== How we manage ECS services ==

Our ECS services use task definitions that point to floating ECR tags,
e.g. 'env.stage'.  By changing where this tag points, we can run new images
in a service without going through a whole Terraform plan/apply.

(See update_ecr_image_tag.sh for more detail on our image tags.)

Once we've updated the value of the tag, we need ECS to redeploy the services
and pick up the new images.  This script does that redeployment, and waits
for services to be stable.

== Usage examples ==

    PIPELINE_DATE="2022-11-09" deploy_catalogue_pipeline.sh tag_images

        This will create a new set of images tagged 'env.2022-11-09' for
        the catalogue pipeline.

    PIPELINE_DATE="2022-12-09" deploy_catalogue_pipeline.sh tag_images_and_deploy_services

        This will create a new set of images tagged 'env.2022-12-09'
        for the catalogue pipeline, then deploy new services in the
        2022-12-09 cluster.

EOF

set -o errexit
set -o nounset

if (( $# == 1 ))
then
  TASK="$1"
else
  echo "Usage: run_sbt_tests.sh [tag_images | tag_images_and_deploy_services]" >&2
  exit 1
fi

if [[ "$TASK" != "tag_images" && "$TASK" != "tag_images_and_deploy_services" ]]
then
  echo "Usage: run_sbt_tests.sh [tag_images | tag_images_and_deploy_services]" >&2
  exit 1
fi

ENV_TAG="env.$PIPELINE_DATE" ./builds/update_ecr_image_tag.sh \
  uk.ac.wellcome/id_minter \
  uk.ac.wellcome/inference_manager \
  uk.ac.wellcome/feature_inferrer \
  uk.ac.wellcome/palette_inferrer \
  uk.ac.wellcome/aspect_ratio_inferrer \
  uk.ac.wellcome/matcher \
  uk.ac.wellcome/merger \
  uk.ac.wellcome/ingestor_images \
  uk.ac.wellcome/ingestor_works \
  uk.ac.wellcome/router \
  uk.ac.wellcome/path_concatenator \
  uk.ac.wellcome/batcher \
  uk.ac.wellcome/relation_embedder \
  uk.ac.wellcome/transformer_calm \
  uk.ac.wellcome/transformer_mets \
  uk.ac.wellcome/transformer_miro \
  uk.ac.wellcome/transformer_sierra \
  uk.ac.wellcome/transformer_tei

if [[ "$TASK" == "tag_images_and_deploy_services" ]]
then
  CLUSTER="pipeline-$PIPELINE_DATE" ./builds/deploy_ecs_services.sh \
    id-minter \
    image-inferrer \
    matcher \
    merger \
    ingestor-images \
    ingestor-works \
    router \
    path_concatenator \
    batcher \
    relation-embedder \
    transformer-calm \
    transformer-mets \
    transformer-miro \
    transformer-sierra \
    transformer-tei
fi

