package weco.pipeline.id_minter

import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import io.circe.Json
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.lambda.Downstream
import weco.pipeline.id_minter.config.models.{
  IdMinterConfig,
  IdMinterConfigurable
}
import weco.pipeline.id_minter.database.RDSIdentifierGenerator
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}

object LambdaMain
    extends IdMinterSqsLambda[IdMinterConfig]
    with IdMinterConfigurable
    with Logging {

  private val identifierGenerator = RDSIdentifierGenerator(
    config.rdsClientConfig,
    config.identifiersTableConfig
  )

  private val upstreamESClient =
    ElasticBuilder.buildElasticClient(config.upstreamElasticConfig)
  private val downstreamESClient =
    ElasticBuilder.buildElasticClient(config.downstreamElasticConfig)

  private val workIndexer =
    new ElasticIndexer[Work[Identified]](
      client = downstreamESClient,
      index = Index(config.targetIndex)
    )

  private val jsonRetriever = new ElasticSourceRetriever[Json](
    client = upstreamESClient,
    index = Index(config.sourceIndex)
  )

  private val minter =
    new MultiIdMinter(
      jsonRetriever,
      new SingleDocumentIdMinter(identifierGenerator)
    )

  override protected val processor =
    new MintingRequestProcessor(minter, workIndexer)
  override protected val downstream: Downstream = Downstream(config.downstreamConfig)
}
