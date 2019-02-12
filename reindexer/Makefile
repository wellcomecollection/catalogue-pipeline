ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= reindexer

PROJECT_ID = reindexer

SBT_APPS 	 =
SBT_SSM_APPS = reindex_worker

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    =

TF_NAME = reindexer
TF_PATH = $(STACK_ROOT)/terraform

$(val $(call stack_setup))
