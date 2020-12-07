ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= api

PROJECT_ID = catalogue_api

SBT_APPS = api
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS = update_api_docs
LAMBDAS 	=

$(val $(call stack_setup))
