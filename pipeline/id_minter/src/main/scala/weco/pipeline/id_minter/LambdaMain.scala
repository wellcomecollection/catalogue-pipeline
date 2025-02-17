package weco.pipeline.id_minter

import com.sksamuel.elastic4s.Index
import io.circe.Json
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.lambda.{Downstream, SQSBatchResponseLambdaApp}
import weco.pipeline.id_minter.config.models.{
  IdMinterConfig,
  IdMinterConfigurable
}
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.catalogue.internal_model.Implicits._
import weco.pipeline.id_minter.database.RDSIdentifierGenerator

import scala.concurrent.Future

class LambdaMain
    extends SQSBatchResponseLambdaApp[String, IdMinterConfig]
    with IdMinterConfigurable {

  private val identifierGenerator = RDSIdentifierGenerator(
    config.rdsClientConfig,
    config.identifiersTableConfig
  )

  private val esClient = ElasticBuilder.buildElasticClient(config.elasticConfig)

  private val workIndexer =
    new ElasticIndexer[Work[Identified]](
      client = esClient,
      index = Index(config.targetIndex)
    )

  private val jsonRetriever = new ElasticSourceRetriever[Json](
    client = esClient,
    index = Index(config.sourceIndex)
  )

  private val minter =
    new MultiIdMinter(
      jsonRetriever,
      new SingleDocumentIdMinter(identifierGenerator)
    )
  val processor = new MintingRequestProcessor(minter, workIndexer)
  val downstream = Downstream(config.downstreamConfig)

  override def processT(t: List[String]): Future[Seq[String]] = {
    processor.process(t).map {
      mintingResponse =>
        downstream.notify(mintingResponse.successes)
        mintingResponse.failures
    }
  }
}
