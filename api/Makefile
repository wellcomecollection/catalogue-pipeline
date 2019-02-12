ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= api

PROJECT_ID = api

SBT_APPS = api

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS = update_api_docs
LAMBDAS 	=

TF_NAME = catalogue_api
TF_PATH = $(STACK_ROOT)/terraform

$(val $(call stack_setup))
