# This file creates a JSON string that gets passed as config to the reindexer.
#
# The structure of the JSON object is something like this:
#
#     {
#       "id1": {
#         "dynamoConfig": {"tableName": "mytable1"},
#         "destinationConfig": {"topicArn": "mytopic1"}
#       },
#       "id2": {
#         "dynamoConfig": {"tableName": "mytable2"},
#         "destinationConfig": {"topicArn": "mytopic2"}
#       }
#     }
#
# It corresponds to a Map[String, ReindexJobConfig] in the Scala classes within
# the reindexer, and tells it what combinations of table -> topic are allowed.

# This template contains the JSON string for a *single* entry in the job config.

locals {
  jobs = { for job in local.reindexer_jobs :
    "${job["source"]}--${job["destination"]}" => {
      source : job["source"],
      dynamoConfig : {
        tableName : job["table"]
      },
      destinationConfig : {
        topicArn : job["topic"]
      }
    }
  }
}

locals {
  reindex_job_config_json = jsonencode(local.jobs)
}

output "reindex_job_config_json" {
  value = local.reindex_job_config_json
}
