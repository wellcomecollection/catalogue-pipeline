package weco.pipeline.merger.fixtures

import com.typesafe.config.Config
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.fixtures.TestWith
import weco.lambda.{ApplicationConfig, Downstream}
import weco.lambda.helpers.MemoryDownstream
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.merger.{MergeProcessor, MergerSQSLambda}
import weco.pipeline.merger.services.{IdentifiedWorkLookup, MergerManager, PlatformMerger}
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

trait MergerFixtures extends MemoryDownstream {
    case class DummyConfig() extends ApplicationConfig

    type WorkOrImage = Either[Work[Merged], Image[Initial]]

    def withMemoryWorkLookup[R](
      identifiedIndex: MemoryRetriever[Work[Identified]]
    )(
      testWith: TestWith[IdentifiedWorkLookup, R]
    ): R = {
      testWith(
        new IdentifiedWorkLookup(identifiedIndex)
      )
    }

    def withMemoryIndexer[R](
      index: mutable.Map[String, WorkOrImage] = mutable.Map.empty
    )(
      testWith: TestWith[MemoryIndexer[WorkOrImage], R]
    ): R = {
      testWith(new MemoryIndexer[WorkOrImage](index = index))
    }

    def withImageDownstream[R](sender: MemoryMessageSender)(
      testWith: TestWith[MemorySNSDownstream, R]
    ): R = {
      testWith(new MemorySNSDownstream(sender))
    }

    def withMergerProcessor[R](
      retriever: MemoryRetriever[Work[Identified]],
      index: mutable.Map[String, WorkOrImage],
    )(testWith: TestWith[MergeProcessor, R]): R = {
      val mergeProcessor = new MergeProcessor(
        sourceWorkLookup = new IdentifiedWorkLookup(retriever),
        mergerManager = new MergerManager(PlatformMerger),
        workOrImageIndexer = new MemoryIndexer(index)
      )
      testWith(mergeProcessor)
    }

    def withMergerSQSLambda[R](
      identifiedIndex: MemoryRetriever[Work[Identified]],
      mergedIndex: mutable.Map[String, WorkOrImage] = mutable.Map.empty,
      imageSender: MemoryMessageSender,
    )(
      testWith: TestWith[MergerSQSLambda[DummyConfig], R]
    ): R = {
      withMemoryWorkLookup(identifiedIndex) {
        retriever =>
          withMemoryIndexer(mergedIndex) {
            indexer =>
              withImageDownstream(imageSender) {
                imageDownstream =>
                  testWith(new MergerSQSLambda[DummyConfig] {
                    override def build(rawConfig: Config): DummyConfig =
                      DummyConfig()

                    override protected val mergeProcessor: MergeProcessor = {
                      new MergeProcessor(
                        retriever,
                        new MergerManager(PlatformMerger),
                        indexer
                      )(global)
                    }
                    override protected val imageMsgSender: Downstream = imageDownstream
                  })
              }
          }
      }
    }

    def getImagesSent(imageSender: MemoryMessageSender): Seq[String] =
      imageSender.messages.map { _.body }
}
