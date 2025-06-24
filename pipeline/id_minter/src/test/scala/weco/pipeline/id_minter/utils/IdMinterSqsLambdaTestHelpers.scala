package weco.pipeline.id_minter.utils

import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax._
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Source}
import weco.fixtures.TestWith
import weco.lambda.helpers.MemoryDownstream
import weco.lambda.{ApplicationConfig, Downstream}
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.database.RDSIdentifierGenerator
import weco.pipeline.id_minter.fixtures.IdentifiersDatabase
import weco.pipeline.id_minter.{IdMinterSqsLambda, MintingRequestProcessor, MultiIdMinter, SingleDocumentIdMinter}
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

trait IdMinterSqsLambdaTestHelpers
  extends IdentifiersDatabase
    with MemoryDownstream {

  case class DummyConfig() extends ApplicationConfig

  def createIndex(works: List[Work[Source]]): Map[String, Json] =
    works.map(work => (work.id, work.asJson)).toMap

  def withMemoryRetriever[R](
                              mergedIndex: Map[String, Json] = Map.empty
                            )(
                              testWith: TestWith[MemoryRetriever[Json], R]
                            ): R = {
    testWith(
      new MemoryRetriever[Json](index = mutable.Map(mergedIndex.toSeq: _*))
    )
  }

  def withMemoryIndexer[R](
                            identifiedIndex: mutable.Map[String, Work[Identified]] = mutable.Map.empty
                          )(
                            testWith: TestWith[MemoryIndexer[Work[Identified]], R]
                          ): R = {
    testWith(new MemoryIndexer[Work[Identified]](index = identifiedIndex))
  }

  def withIdMinterSQSLambda[R](
                                identifiersTableConfig: IdentifiersTableConfig,
                                memoryDownstream: Downstream,
                                mergedIndex: Map[String, Json] = Map.empty,
                                identifiedIndex: mutable.Map[String, Work[Identified]]
                              )(
                                testWith: TestWith[IdMinterSqsLambda[DummyConfig], R]
                              ): R = {
    withMemoryRetriever(mergedIndex) {
      retriever =>
        withMemoryIndexer(identifiedIndex) {
          indexer =>
            testWith(new IdMinterSqsLambda[DummyConfig] {
              val idGenerator = RDSIdentifierGenerator(
                rdsClientConfig,
                identifiersTableConfig
              )

              override def build(rawConfig: Config): DummyConfig =
                DummyConfig()

              override protected val processor: MintingRequestProcessor =
                new MintingRequestProcessor(
                  new MultiIdMinter(
                    retriever,
                    new SingleDocumentIdMinter(idGenerator)
                  ),
                  indexer
                )(global)
              override protected val downstream: Downstream = memoryDownstream
            })
        }
    }
  }
}
