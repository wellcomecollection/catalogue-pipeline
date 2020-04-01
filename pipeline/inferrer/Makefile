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

$(val $(call stack_setup))
