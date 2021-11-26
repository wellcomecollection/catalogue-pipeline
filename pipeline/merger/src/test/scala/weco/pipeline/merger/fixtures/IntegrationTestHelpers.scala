package weco.pipeline.merger.fixtures

import io.circe.Encoder
import org.scalatest.EitherValues
import org.scalatest.matchers.{MatchResult, Matcher}
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.RetrieverMultiResult
import weco.pipeline_storage.memory.MemoryRetriever

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

// These are in a separate file to avoid cluttering up the integration tests
// with code that doesn't tell us about the desired matcher/merger behaviour.
trait IntegrationTestHelpers extends EitherValues with MatcherFixtures with MergerFixtures {

  type MergerIndex = mutable.Map[String, WorkOrImage]
  type Context = (MemoryRetriever[Work[WorkState.Identified]], QueuePair, QueuePair, MemoryMessageSender, MemoryMessageSender, MergerIndex)

  implicit class ContextOps(context: Context) {
    def getMerged(originalWork: Work[WorkState.Identified]): Work[WorkState.Merged] = {
      val (_, _, _, _, _, mergedIndex) = context
      mergedIndex(originalWork.state.canonicalId.underlying).left.value
    }
  }

  def withContext[R](testWith: TestWith[Context, R]): R = {
    val retriever: MemoryRetriever[Work[WorkState.Identified]] =
      new MemoryRetriever[Work[WorkState.Identified]]()

    val matcherRetriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]() {
        override def apply(ids: Seq[String]): Future[RetrieverMultiResult[WorkStub]] =
          retriever.apply(ids).map { multiResult =>
            RetrieverMultiResult(
              found = multiResult.found.map { case (id, work) => id -> WorkStub(work) },
              notFound = multiResult.notFound
            )
          }(global)
      }

    withLocalSqsQueuePair() { matcherQueuePair =>
      withLocalSqsQueuePair() { mergerQueuePair =>
        val matcherSender = new MemoryMessageSender() {
          override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = {
            sendNotificationToSQS(mergerQueuePair.queue, t)
            super.sendT(t)
          }
        }

        val workSender = new MemoryMessageSender()
        val imageSender = new MemoryMessageSender()

        val mergerIndex = mutable.Map[String, WorkOrImage]()

        withMatcherService(matcherRetriever, matcherQueuePair.queue, matcherSender) { _ =>
          withMergerService(retriever, mergerQueuePair.queue, workSender, imageSender, index = mergerIndex) { _ =>
            testWith((retriever, matcherQueuePair, mergerQueuePair, workSender, imageSender, mergerIndex))
          }
        }
      }
    }
  }

  def processWorks(works: Seq[Work[WorkState.Identified]])(implicit context: Context): Unit = {
    val (retriever, matcherQueuePair, mergerQueuePair, workSender, imageSender, mergerIndex) = context

    works.foreach { w =>
      println(s"Processing work ${w.state.sourceIdentifier} (${w.state.canonicalId})")

      // Add the work to the retriever and send it to the matcher, as if it's
      // just been processed by the ID minter.
      retriever.index ++= Map(w.state.canonicalId.underlying -> w)
      sendNotificationToSQS(matcherQueuePair.queue, body = w.state.canonicalId.underlying)

      // Check all the queues are eventually drained as the message moves through
      // the matcher and the merger.
      eventually {
        assertQueueEmpty(matcherQueuePair.queue)
        assertQueueEmpty(matcherQueuePair.dlq)
        assertQueueEmpty(mergerQueuePair.queue)
        assertQueueEmpty(mergerQueuePair.dlq)
      }

      // Check that the merger has notified the next application about everything
      // in the index.  This check could be more robust, but it'll do for now.
      val idsSentByTheMerger = (workSender.messages ++ imageSender.messages).map(_.body).toSet
      mergerIndex.keySet -- idsSentByTheMerger shouldBe empty
    }
  }

  def processWork(work: Work[WorkState.Identified])(implicit context: Context): Unit =
    processWorks(Seq(work))

  class StateMatcher(right: WorkState.Identified) extends Matcher[WorkState.Merged] {
    def apply(left: WorkState.Merged): MatchResult =
      MatchResult(
        left.sourceIdentifier == right.sourceIdentifier &&
          left.canonicalId == right.canonicalId &&
          left.sourceModifiedTime == right.sourceModifiedTime,
        s"${left.canonicalId} has different state to ${right.canonicalId}",
        s"${left.canonicalId} has similar state to ${right.canonicalId}"
    )
  }

  def beSimilarTo(expectedRedirectTo: WorkState.Identified) =
    new StateMatcher(expectedRedirectTo)

  // TODO: Upstream this into scala-libs
  class InstantMatcher(within: Duration) extends Matcher[Instant] {
    override def apply(left: Instant): MatchResult = {
      MatchResult(
        (Instant.now().toEpochMilli - left.toEpochMilli) < within.toMillis,
        s"$left is not recent",
        s"$left is recent"
      )
    }
  }

  def beRecent(within: Duration = 3 seconds) =
    new InstantMatcher(within)
}
