package weco.pipeline.reindex_worker.config

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.messaging.sns.SNSConfig
import weco.pipeline.reindex_worker.models.ReindexJobConfig
import weco.pipeline.reindex_worker.models.ReindexSource.Mets
import weco.storage.dynamo.DynamoConfig

class ReindexJobConfigBuilderTest extends AnyFunSpec with Matchers {
  it("reads the config") {
    // This is the config for the reindex worker at 2020-12-16
    val config =
      """
        |{
        |  "sierra--reporting": {
        |    "source": "sierra",
        |    "dynamoConfig": {
        |      "tableName": "vhs-sierra-sierra-adapter-20200604"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:reporting_sierra_reindex_topic"
        |    }
        |  },
        |  "sierra--catalogue": {
        |    "source": "sierra",
        |    "dynamoConfig": {
        |      "tableName": "vhs-sierra-sierra-adapter-20200604"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:catalogue_sierra_reindex_topic"
        |    }
        |  },
        |  "miro--reporting": {
        |    "source": "miro",
        |    "dynamoConfig": {
        |      "tableName": "vhs-sourcedata-miro"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:reporting_miro_reindex_topic"
        |    }
        |  },
        |  "miro--catalogue": {
        |    "source": "miro",
        |    "dynamoConfig": {
        |      "tableName": "vhs-sourcedata-miro"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:catalogue_miro_reindex_topic"
        |    }
        |  },
        |  "miro_inventory--reporting": {
        |    "source": "miro_inventory",
        |    "dynamoConfig": {
        |      "tableName": "vhs-miro-migration"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:reporting_miro_inventory_reindex_topic"
        |    }
        |  },
        |  "mets--catalogue": {
        |    "source": "mets",
        |    "dynamoConfig": {
        |      "tableName": "mets-adapter-store-delta"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:mets_reindexer_topic"
        |    }
        |  },
        |  "calm--catalogue": {
        |    "source": "calm",
        |    "dynamoConfig": {
        |      "tableName": "vhs-calm-adapter"
        |    },
        |    "destinationConfig": {
        |      "topicArn": "arn:aws:sns:eu-west-1:760097843905:calm_reindexer_topic"
        |    }
        |  }
        |}
        |""".stripMargin

    val configMap = ReindexJobConfigBuilder.buildReindexJobConfigMap(config)

    configMap("mets--catalogue") shouldBe ReindexJobConfig(
      dynamoConfig = DynamoConfig(tableName = "mets-adapter-store-delta"),
      destinationConfig = SNSConfig(
        topicArn = "arn:aws:sns:eu-west-1:760097843905:mets_reindexer_topic"
      ),
      source = Mets
    )
  }
}
