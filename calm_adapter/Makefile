ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT = calm_adapter

PROJECT_ID = calm_adapter

SBT_APPS = calm_adapter calm_deletion_checker
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES =
SBT_NO_DOCKER_LIBRARIES = calm_api_client

PYTHON_APPS =
LAMBDAS = calm_window_generator calm_deletion_check_initiator

# These override the defaults (catalogue account) set in functions.Makefile
# See https://github.com/wellcomecollection/catalogue/pull/912 for some context
LAMBDA_UPLOAD_BUCKET = wellcomecollection-platform-infra
LAMBDA_PUBLISH_ROLE_ARN := arn:aws:iam::760097843905:role/platform-developer

$(val $(call stack_setup))
