ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT  = goobi_adapter

SBT_APPS = goobi_reader

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    =

TF_NAME = goobi_adapter
TF_PATH = $(STACK_ROOT)/terraform

$(val $(call stack_setup))
