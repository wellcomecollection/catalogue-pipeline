ROOT = $(shell git rev-parse --show-toplevel)
STACK_ROOT 	= pipeline

include $(ROOT)/makefiles/functions.Makefile

SBT_APPS 	 =
SBT_SSM_APPS = id_minter ingestor recorder matcher merger

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    =

TF_NAME = catalogue_pipeline
TF_PATH = $(STACK_ROOT)/terraform

$(val $(call stack_setup))

include $(ROOT)/$(STACK_ROOT)/transformer/Makefile
