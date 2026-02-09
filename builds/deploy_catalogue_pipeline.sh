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

ROOT=$(git rev-parse --show-toplevel)

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

ENV_TAG="env.$PIPELINE_DATE" "$ROOT/builds/update_ecr_image_tag.sh" \
  uk.ac.wellcome/inference_manager \
  uk.ac.wellcome/feature_inferrer \
  uk.ac.wellcome/palette_inferrer \
  uk.ac.wellcome/aspect_ratio_inferrer \
  uk.ac.wellcome/id_minter \
  uk.ac.wellcome/matcher \
  uk.ac.wellcome/merger \
  uk.ac.wellcome/ingestor_images \
  uk.ac.wellcome/transformer_calm \
  uk.ac.wellcome/transformer_mets \
  uk.ac.wellcome/transformer_miro \
  uk.ac.wellcome/transformer_sierra \
  uk.ac.wellcome/transformer_tei \
  uk.ac.wellcome/unified_pipeline_lambda
  
if [[ "$TASK" == "tag_images_and_deploy_services" ]]
then
  echo "Deploying ECS pipeline services to catalogue-$PIPELINE_DATE"
  CLUSTER="catalogue-$PIPELINE_DATE" "$ROOT/builds/deploy_ecs_services.sh" \
    image_inferrer \
    ingestor_images \
    transformer_calm \
    transformer_mets \
    transformer_miro \
    transformer_sierra \
    transformer_tei

  echo "Deploying λ pipeline services to catalogue-$PIPELINE_DATE"
  "$ROOT/builds/deploy_lambda_services.sh" \
    id_minter:id_minter \
    id_minter:id_minter_step_function \
    matcher:matcher \
    merger:merger
fi

