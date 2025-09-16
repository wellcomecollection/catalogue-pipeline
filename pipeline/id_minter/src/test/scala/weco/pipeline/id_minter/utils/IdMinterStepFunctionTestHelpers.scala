package weco.pipeline.id_minter.utils

import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax._
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Source}
import weco.fixtures.TestWith
import weco.lambda.ApplicationConfig
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.database.RDSIdentifierGenerator
import weco.pipeline.id_minter.fixtures.IdentifiersDatabase
import weco.pipeline.id_minter.{
  IdMinterStepFunctionLambda,
  MintingRequestProcessor,
  MultiIdMinter,
  SingleDocumentIdMinter
}
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

case class StepFunctionTestConfig() extends ApplicationConfig

trait IdMinterStepFunctionTestHelpers extends IdentifiersDatabase {
  implicit val ec: ExecutionContext = ExecutionContext.global

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

  def withIdMinterStepFunctionLambda[R](
    identifiersTableConfig: IdentifiersTableConfig,
    mergedIndex: Map[String, Json] = Map.empty,
    identifiedIndex: mutable.Map[String, Work[Identified]] = mutable.Map.empty
  )(
    testWith: TestWith[IdMinterStepFunctionLambda[StepFunctionTestConfig], R]
  ): R = {
    val idGenerator = RDSIdentifierGenerator(
      rdsClientConfig,
      identifiersTableConfig
    )

    withMemoryRetriever(mergedIndex) {
      retriever =>
        withMemoryIndexer(identifiedIndex) {
          indexer =>
            val lambda =
              new IdMinterStepFunctionLambda[StepFunctionTestConfig] {
                override val config: StepFunctionTestConfig =
                  StepFunctionTestConfig()
                override def build(rawConfig: Config): StepFunctionTestConfig =
                  StepFunctionTestConfig()

                override protected val processor: MintingRequestProcessor =
                  new MintingRequestProcessor(
                    new MultiIdMinter(
                      retriever,
                      new SingleDocumentIdMinter(idGenerator)
                    ),
                    indexer
                  )(ExecutionContext.global)
              }

            testWith(lambda)
        }
    }
  }

  def withIdMinterStepFunctionLambdaBuilder[R](
    mergedIndex: Map[String, Json] = Map.empty,
    identifiedIndex: mutable.Map[String, Work[Identified]] = mutable.Map.empty
  )(
    testWith: TestWith[
      IdentifiersTableConfig => IdMinterStepFunctionLambda[
        StepFunctionTestConfig
      ],
      R
    ]
  ): R = {
    def buildLambda(
      identifiersTableConfig: IdentifiersTableConfig
    ): IdMinterStepFunctionLambda[StepFunctionTestConfig] = {
      val idGenerator = RDSIdentifierGenerator(
        rdsClientConfig,
        identifiersTableConfig
      )

      new IdMinterStepFunctionLambda[StepFunctionTestConfig] {
        override val config: StepFunctionTestConfig = StepFunctionTestConfig()
        override def build(rawConfig: Config): StepFunctionTestConfig =
          StepFunctionTestConfig()

        override protected val processor: MintingRequestProcessor =
          new MintingRequestProcessor(
            new MultiIdMinter(
              new MemoryRetriever[Json](index =
                mutable.Map(mergedIndex.toSeq: _*)
              ),
              new SingleDocumentIdMinter(idGenerator)
            ),
            new MemoryIndexer[Work[Identified]](index = identifiedIndex)
          )(ExecutionContext.global)
      }
    }

    testWith(buildLambda)
  }
}
