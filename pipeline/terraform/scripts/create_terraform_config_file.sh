#!/usr/bin/env bash

set -o errexit
set -o nounset

# Record the pipeline date and the backend configuration in a file.
#
# Note: we create a file rather than passing command-line arguments because:
#
#   1.  It gives us an easy way to detect if any of this config has changed
#       e.g. when the folder has been copied/renamed
#
#   2.  You have to pass different command-line arguments depending on
#       which command is being run (e.g. init wants something different
#       to plan or apply)
#
#   3.  It makes it clearer what's going on to a human reader.  If you
#       stopped using this script, you still get a working TF configuration.
#
cat > pipeline_config.tf.tmp <<EOF
# Note: this file is autogenerated by the run_terraform.sh script.
#
# Edits to this file may be reverted!

locals {
  pipeline_date = "$PIPELINE_DATE"
}

terraform {
  backend "s3" {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    }

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue-pipeline/pipeline/$PIPELINE_DATE.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}
EOF

# If the config file doesn't exist yet, then blat any saved .terraform
# config and copy it into place.
if [[ ! -f pipeline_config.tf ]]
then
  rm -rf .terraform
  mv pipeline_config.tf.tmp pipeline_config.tf
fi

# If the config file exists but it's different, then blat any saved
# .terraform config and replace the old config file.
if [[ -f pipeline_config.tf ]]
then
  if ! cmp -s pipeline_config.tf.tmp pipeline_config.tf
  then
    rm -rf .terraform
  fi
  mv pipeline_config.tf.tmp pipeline_config.tf
fi
