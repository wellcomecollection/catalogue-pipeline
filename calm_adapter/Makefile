ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT = calm_adapter

PROJECT_ID = calm_adapter

SBT_APPS = calm_adapter calm_deletion_checker
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS = calm_window_generator calm_deletion_check_initiator

$(val $(call stack_setup))
