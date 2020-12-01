ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT = pipeline

PROJECT_ID = catalogue_pipeline

SBT_APPS = matcher merger

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS =
LAMBDAS     =

$(val $(call stack_setup))

include $(ROOT)/pipeline/transformer/Makefile
include $(ROOT)/pipeline/inferrer/Makefile
include $(ROOT)/pipeline/id_minter/Makefile
include $(ROOT)/pipeline/ingestor/Makefile
include $(ROOT)/pipeline/relation_embedder/Makefile
