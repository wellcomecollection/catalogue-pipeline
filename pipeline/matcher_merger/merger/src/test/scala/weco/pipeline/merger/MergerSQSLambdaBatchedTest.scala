package weco.pipeline.merger

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.lambda.{
  SQSLambdaMessage,
  SQSLambdaMessageFailedRetryable,
  SQSLambdaMessageProcessed
}
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline_storage.{Indexable, Indexer}
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MergerSQSLambdaBatchedTest extends MergerSQSLambdaTest {
  override protected val batched: Boolean = true

  it("retries only the message whose document failed to index") {
    val okWork = identifiedWork()
    val badWork = identifiedWork()

    val identifiedIndex = new MemoryRetriever[Work[WorkState.Identified]]()
    identifiedIndex.index ++= Map(okWork.id -> okWork, badWork.id -> badWork)
    val mergedIndex = mutable.Map[String, WorkOrImage]()

    // Fails any document with badWork's id and indexes the rest.
    val failingIndexer = new Indexer[WorkOrImage] {
      private val underlying = new MemoryIndexer[WorkOrImage](mergedIndex)
      def init(): Future[Unit] = Future.successful(())
      def apply(
        docs: Seq[WorkOrImage]
      ): Future[Either[Seq[WorkOrImage], Seq[WorkOrImage]]] = {
        val (bad, good) = docs.partition(
          doc => implicitly[Indexable[WorkOrImage]].id(doc) == badWork.id
        )
        underlying(good).map(_ => if (bad.isEmpty) Right(good) else Left(bad))
      }
    }

    withMergerSQSLambda(
      identifiedIndex,
      mergedIndex,
      new MemoryMessageSender(),
      batched = true,
      indexerOverride = Some(failingIndexer)
    ) {
      mergerSQSLambda =>
        val okId = randomUUID.toString
        val badId = randomUUID.toString
        val messages = List(
          SQSLambdaMessage(okId, createMatcherResultWith(Set(Set(okWork)))),
          SQSLambdaMessage(badId, createMatcherResultWith(Set(Set(badWork))))
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          results =>
            results should contain(SQSLambdaMessageProcessed(okId))
            results.collect {
              case failure: SQSLambdaMessageFailedRetryable => failure.messageId
            } shouldBe Seq(badId)
            mergedIndex.keySet shouldBe Set(okWork.id)
        }
    }
  }
}
