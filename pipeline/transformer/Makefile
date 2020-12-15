ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/transformer

PROJECT_ID = catalogue_pipeline

SBT_APPS = transformer_calm transformer_mets transformer_miro transformer_sierra
SBT_NO_DOCKER_APPS =

SBT_DOCKER_LIBRARIES    = transformer_common
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS     =

$(val $(call stack_setup))
