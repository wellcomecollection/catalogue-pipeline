ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= api/stacks

PROJECT_ID = stacks

SBT_APPS =
SBT_NO_DOCKER_APPS = items_api requests_api

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS 	=

$(val $(call stack_setup))
