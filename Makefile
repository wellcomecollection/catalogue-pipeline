export ECR_BASE_URI = 760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome
export REGISTRY_ID  = 760097843905

include makefiles/functions.Makefile
include makefiles/formatting.Makefile

include api/Makefile
include common/Makefile
include goobi_adapter/Makefile
include pipeline/Makefile
include reindexer/Makefile
include sierra_adapter/Makefile
include snapshots/Makefile
