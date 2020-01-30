ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT = calm_adapter

PROJECT_ID = calm_adapter

SBT_APPS = calm_adapter

SBT_DOCKER_LIBRARIES =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS =

TF_NAME = calm_adapter
TF_PATH = $(STACK_ROOT)/terraform

$(val $(call stack_setup))
