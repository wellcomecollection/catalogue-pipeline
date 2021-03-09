ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/inferrer

PROJECT_ID = catalogue_pipeline

SBT_APPS = inference_manager
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS = feature_inferrer feature_training palette_inferrer
LAMBDAS     =

TEST_OVERRIDE := feature_inferrer palette_inferrer

inference_manager-test: feature_inferrer-build palette_inferrer-build

inference_manager-integration-only: feature_inferrer-build palette_inferrer-build
	$(ROOT)/docker_run.py --dind --sbt --root -- \
		--net host \
		wellcome/sbt_wrapper \
		"project inference_manager" ";dockerComposeUp;testOnly **.integration.*;dockerComposeStop"

feature_inferrer-test:
	$(call test_python,pipeline/inferrer/feature_inferrer)
	$(MAKE) inference_manager-integration-only

palette_inferrer-test:
	$(call test_python,pipeline/inferrer/palette_inferrer)
	$(MAKE) inference_manager-integration-only

$(val $(call stack_setup))
