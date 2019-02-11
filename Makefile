export ECR_BASE_URI = 975596993436.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome
export REGISTRY_ID  = 975596993436

include makefiles/functions.Makefile
include makefiles/formatting.Makefile

include common/Makefile
include goobi_adapter/Makefile
include pipeline/Makefile
include reindexer/Makefile
