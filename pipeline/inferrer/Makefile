ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/inferrer

PROJECT_ID = catalogue_pipeline

SBT_APPS = inference_manager

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS = feature_inferrer
LAMBDAS     =

TF_NAME =
TF_PATH =

inference_manager-test: feature_inferrer-build

inference_manager-integration-only:
	$(ROOT)/docker_run.py --dind --sbt --root -- \
		--net host \
		wellcome/sbt_wrapper \
		"project inference_manager" ";dockerComposeUp;testOnly **.integration.*;dockerComposeStop"

TEST_OVERRIDE = feature_inferrer
feature_inferrer-test: feature_inferrer-build
	$(ROOT)/docker_run.py --dind -- feature_inferrer \
		python -m compileall /app
	$(MAKE) inference_manager-integration-only

$(val $(call stack_setup))
