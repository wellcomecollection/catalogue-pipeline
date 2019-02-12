ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT  = common

SBT_APPS 	 =
SBT_SSM_APPS =

SBT_DOCKER_LIBRARIES    = elasticsearch
SBT_NO_DOCKER_LIBRARIES = display elasticsearch_typesafe internal_model

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    =

TF_NAME =
TF_PATH =

$(val $(call stack_setup))
