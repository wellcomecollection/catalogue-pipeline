ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT  = common

SBT_APPS =
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    = internal_model pipeline_storage
SBT_NO_DOCKER_LIBRARIES = display source_model source_model_typesafe

PYTHON_APPS =
LAMBDAS     =

$(val $(call stack_setup))
