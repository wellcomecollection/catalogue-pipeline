ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/transformer

PROJECT_ID = catalogue_pipeline

SBT_APPS 	 =
SBT_SSM_APPS = transformer_miro transformer_sierra

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    =

TF_NAME =
TF_PATH =

$(val $(call stack_setup))
