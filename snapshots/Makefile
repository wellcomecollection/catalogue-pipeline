ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/makefiles/functions.Makefile

STACK_ROOT  = snapshots

SBT_APPS 	 =
SBT_SSM_APPS = snapshot_generator

SBT_DOCKER_LIBRARIES    =
SBT_NO_DOCKER_LIBRARIES =

PYTHON_APPS     =
PYTHON_SSM_APPS =
LAMBDAS 	    = snapshot_scheduler snapshot_slack_alarms

TF_NAME =
TF_PATH =

TF_IS_PUBLIC_FACING = false

$(val $(call stack_setup))
