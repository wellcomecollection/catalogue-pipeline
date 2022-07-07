#!/usr/bin/env bash

set -o errexit
set -o nounset


# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_read["source"]' mRDnSB8zSZK3ipg-SmlHtA/works-source_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_read["indexed"]' mRDnSB8zSZK3ipg-SmlHtA/works-indexed_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_read["merged"]' mRDnSB8zSZK3ipg-SmlHtA/works-merged_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_read["identified"]' mRDnSB8zSZK3ipg-SmlHtA/works-identified_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_read["denormalised"]' mRDnSB8zSZK3ipg-SmlHtA/works-denormalised_read
#
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_write["source"]' mRDnSB8zSZK3ipg-SmlHtA/works-source_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_write["indexed"]' mRDnSB8zSZK3ipg-SmlHtA/works-indexed_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_write["merged"]' mRDnSB8zSZK3ipg-SmlHtA/works-merged_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_write["identified"]' mRDnSB8zSZK3ipg-SmlHtA/works-identified_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.works_write["denormalised"]' mRDnSB8zSZK3ipg-SmlHtA/works-denormalised_write
#
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_read["initial"]' mRDnSB8zSZK3ipg-SmlHtA/images-initial_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_read["indexed"]' mRDnSB8zSZK3ipg-SmlHtA/images-indexed_read
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_read["augmented"]' mRDnSB8zSZK3ipg-SmlHtA/images-augmented_read
#
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_write["initial"]' mRDnSB8zSZK3ipg-SmlHtA/images-initial_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_write["indexed"]' mRDnSB8zSZK3ipg-SmlHtA/images-indexed_write
# ./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.elasticstack_elasticsearch_security_role.images_write["augmented"]' mRDnSB8zSZK3ipg-SmlHtA/images-augmented_write

./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.module.users["transformer"].random_password.password' $(AWS_PROFILE=platform-dev aws secretsmanager get-secret-value --secret-id elasticsearch/pipeline_storage_2022-06-18/transformer/es_password | jq -r '.SecretString')
./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.module.users["transformer"].elasticstack_elasticsearch_security_user.user' "mRDnSB8zSZK3ipg-SmlHtA/transformer"

./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.module.users["transformer"].module.secrets.aws_secretsmanager_secret.secret["elasticsearch/pipeline_storage_2022-06-18/transformer/es_password"]' $(AWS_PROFILE=platform-dev aws secretsmanager get-secret-value --secret-id 'elasticsearch/pipeline_storage_2022-06-18/transformer/es_password' | jq -r .ARN)
./run_terraform.sh import 'module.catalogue_pipeline_2022-06-18.module.users["transformer"].module.secrets.aws_secretsmanager_secret.secret["elasticsearch/pipeline_storage_2022-06-18/transformer/es_username"]' $(AWS_PROFILE=platform-dev aws secretsmanager get-secret-value --secret-id 'elasticsearch/pipeline_storage_2022-06-18/transformer/es_username' | jq -r .ARN)