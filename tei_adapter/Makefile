ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT = tei_adapter

PROJECT_ID = tei_adapter

SBT_APPS = tei_id_extractor tei_adapter
SBT_NO_DOCKER_APPS = tei_models

SBT_DOCKER_LIBRARIES =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS = tei_updater

# These override the defaults (catalogue account) set in functions.Makefile
# See https://github.com/wellcomecollection/catalogue/pull/912 for some context
LAMBDA_UPLOAD_BUCKET = wellcomecollection-platform-infra
LAMBDA_PUBLISH_ROLE_ARN := arn:aws:iam::760097843905:role/platform-developer

$(val $(call stack_setup))
