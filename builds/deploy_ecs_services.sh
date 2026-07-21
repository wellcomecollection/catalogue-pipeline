#!/usr/bin/env bash
<<EOF
Trigger a deployment of an ECS service.

This script is mirrored in our other Scala repos.

== How we manage ECS services ==

Our ECS services use task definitions that point to floating ECR tags,
e.g. 'env.stage'.  By changing where this tag points, we can run new images
in a service without going through a whole Terraform plan/apply.

(See update_ecr_image_tag.sh for more detail on our image tags.)

Once we've updated the value of the tag, we need ECS to redeploy the services
and pick up the new images.  This script does that redeployment, and waits
for services to be stable.

== Usage examples ==

    CLUSTER="catalogue-api-2021-04-26" redeploy_ecs_services.sh stage-concepts-api

        This will redeploy the 'stage-concepts-api' service in the
        'catalogue-api-2021-04-26' cluster.

    CLUSTER="catalogue-api-2021-04-26" redeploy_ecs_services.sh stage-concepts-api stage-items-api stage-search-api

        This will update redeploy three services in the cluster.

        More generally you can supply an arbitrary number of ECS services as
        additional arguments, and they will all be redeployed.

EOF

set -o errexit
set -o nounset

# Not every pipeline runs every service (newer pipeline stacks are composed
# from a subset), so skip services that don't exist in this cluster.
EXISTING_SERVICES=""
for serviceName in "$@"
do
  status=$(aws ecs describe-services \
    --cluster "$CLUSTER" \
    --services "$serviceName" \
    --query 'services[0].status' \
    --output text 2>/dev/null || echo "MISSING")
  if [[ "$status" == "ACTIVE" ]]
  then
    EXISTING_SERVICES="$EXISTING_SERVICES $serviceName"
  else
    echo "WARNING: skipping $serviceName, not present in $CLUSTER"
  fi
done

for serviceName in $EXISTING_SERVICES
do
  echo "Forcing a new deployment of $serviceName in $CLUSTER"
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$serviceName" \
    --force-new-deployment >/dev/null
done

for serviceName in $EXISTING_SERVICES
do
  echo "Waiting for $serviceName to be stable"
  aws ecs wait services-stable \
    --cluster "$CLUSTER" \
    --service "$serviceName"
  echo "Done! $serviceName is stable"
done
