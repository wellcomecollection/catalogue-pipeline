ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/ingestor

PROJECT_ID = catalogue_pipeline

SBT_APPS = ingestor_works ingestor_images

SBT_DOCKER_LIBRARIES    = ingestor_common
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS     =

TF_NAME =
TF_PATH =

$(val $(call stack_setup))
