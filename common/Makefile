ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT  = common

SBT_APPS =
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    = internal_model pipeline_storage
SBT_NO_DOCKER_LIBRARIES = display source_model source_model_typesafe

PYTHON_APPS =
LAMBDAS     = window_generator

# These override the defaults (catalogue account) set in functions.Makefile
# See https://github.com/wellcomecollection/catalogue/pull/912 for some context
LAMBDA_UPLOAD_BUCKET = wellcomecollection-platform-infra
LAMBDA_PUBLISH_ROLE_ARN := arn:aws:iam::760097843905:role/platform-developer

$(val $(call stack_setup))
