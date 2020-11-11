ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT 	= pipeline/relation_embedder

PROJECT_ID = catalogue_pipeline

SBT_APPS = relation_embedder router

SBT_DOCKER_LIBRARIES    = transformer_common
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS     =

TF_NAME =
TF_PATH =

$(val $(call stack_setup))
