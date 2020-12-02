ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/id_minter

PROJECT_ID = catalogue_pipeline

SBT_APPS = id_minter_works id_minter_images
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    = id_minter_common
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS     =

$(val $(call stack_setup))
