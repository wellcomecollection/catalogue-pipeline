package weco.pipeline.merger.fixtures

import io.circe.Encoder
import org.scalatest.EitherValues
import org.scalatest.matchers.{MatchResult, Matcher}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.generators.WorkGenerators
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
trait IntegrationTestHelpers
    extends EitherValues
    with MatcherFixtures
    with MergerFixtures
    with WorkGenerators {

  type MergerIndex = mutable.Map[String, WorkOrImage]
  type Context = (
    MemoryRetriever[Work[WorkState.Identified]],
    QueuePair,
    QueuePair,
    MemoryWorkRouter,
    MemoryMessageSender,
    MergerIndex
  )

  implicit class ContextOps(context: Context) {
    private val index = {
      val (_, _, _, _, _, mergedIndex) = context
      mergedIndex
    }

    def getMerged(
      originalWork: Work[WorkState.Identified]
    ): Work[WorkState.Merged] =
      index(originalWork.state.canonicalId.underlying).left.value

    def imageData: Seq[ImageData[IdState.Identified]] =
      index.values.collect {
        case Right(im) =>
          ImageData(
            id = IdState.Identified(
              canonicalId = im.state.canonicalId,
              sourceIdentifier = im.state.sourceIdentifier
            ),
            version = im.version,
            locations = im.locations
          )
      }.toSeq
  }

  def withContext[R](testWith: TestWith[Context, R]): R = {
    val retriever: MemoryRetriever[Work[WorkState.Identified]] =
      new MemoryRetriever[Work[WorkState.Identified]]()

    val matcherRetriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]() {
        override def apply(
          ids: Seq[String]
        ): Future[RetrieverMultiResult[WorkStub]] =
          retriever
            .apply(ids)
            .map {
              multiResult =>
                RetrieverMultiResult(
                  found = multiResult.found.map {
                    case (id, work) => id -> WorkStub(work)
                  },
                  notFound = multiResult.notFound
                )
            }(global)
      }

    withLocalSqsQueuePair() {
      matcherQueuePair =>
        withLocalSqsQueuePair() {
          mergerQueuePair =>
            val matcherSender = new MemoryMessageSender() {
              override def sendT[T](
                t: T
              )(implicit encoder: Encoder[T]): Try[Unit] = {
                sendNotificationToSQS(mergerQueuePair.queue, t)
                super.sendT(t)
              }
            }

            val workRouter = new MemoryWorkRouter(
              workSender = new MemoryMessageSender(): MemoryMessageSender,
              pathSender = new MemoryMessageSender(): MemoryMessageSender,
              pathConcatenatorSender = new MemoryMessageSender(): MemoryMessageSender)

            val imageSender = new MemoryMessageSender()

            val mergerIndex = mutable.Map[String, WorkOrImage]()

            withMatcherService(
              matcherRetriever,
              matcherQueuePair.queue,
              matcherSender
            ) {
              _ =>
                withMergerService(
                  retriever,
                  mergerQueuePair.queue,
                  workRouter,
                  imageSender,
                  index = mergerIndex
                ) {
                  _ =>
                    testWith(
                      (
                        retriever,
                        matcherQueuePair,
                        mergerQueuePair,
                        workRouter,
                        imageSender,
                        mergerIndex
                      )
                    )
                }
            }
        }
    }
  }

  def processWorks(
    works: Work[WorkState.Identified]*
  )(implicit context: Context): Unit = {
    val (
      retriever,
      matcherQueuePair,
      mergerQueuePair,
      workRouter,
      imageSender,
      mergerIndex
    ) = context

    works.foreach {
      w =>
        println(
          s"Processing work ${w.state.sourceIdentifier} (${w.state.canonicalId})"
        )

        // Add the work to the retriever and send it to the matcher, as if it's
        // just been processed by the ID minter.
        retriever.index ++= Map(w.state.canonicalId.underlying -> w)
        sendNotificationToSQS(
          matcherQueuePair.queue,
          body = w.state.canonicalId.underlying
        )

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
        val idsSentByTheMerger = (
          workRouter.workSender.messages ++
          workRouter.pathSender.messages ++
          workRouter.pathConcatenatorSender.messages ++
          imageSender.messages
          ).map(_.body).toSet
        mergerIndex.keySet.size == idsSentByTheMerger.size
    }
  }

  def processWork(work: Work[WorkState.Identified])(
    implicit context: Context
  ): Unit =
    processWorks(work)

  def updateInternalWork(
                          internalWork: Work.Visible[WorkState.Identified],
                          teiWork: Work.Visible[WorkState.Identified]
                        ) =
    internalWork
      .copy(version = teiWork.version)
      .mapState(
        state =>
          state.copy(sourceModifiedTime = teiWork.state.sourceModifiedTime)
      )

  class StateMatcher(right: WorkState.Identified)
    extends Matcher[WorkState.Merged] {
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

  class RedirectMatcher(expectedRedirectTo: Work.Visible[Identified])
    extends Matcher[Work[Merged]] {
    def apply(left: Work[Merged]): MatchResult = {
      left match {
        case w: Work.Redirected[Merged] =>
          MatchResult(
            w.redirectTarget.sourceIdentifier == expectedRedirectTo.sourceIdentifier,
            s"${left.sourceIdentifier} was redirected to ${w.redirectTarget.sourceIdentifier}, not ${expectedRedirectTo.sourceIdentifier}",
            s"${left.sourceIdentifier} was redirected correctly"
          )

        case _ =>
          MatchResult(
            matches = false,
            s"${left.sourceIdentifier} was not redirected at all",
            s"${left.sourceIdentifier} was redirected correctly"
          )
      }
    }
  }

  def beRedirectedTo(expectedRedirectTo: Work.Visible[Identified]) =
    new RedirectMatcher(expectedRedirectTo)

  def beVisible = new Matcher[Work[Merged]] {
    override def apply(left: Work[Merged]): MatchResult =
      MatchResult(
        left.isInstanceOf[Work.Visible[Merged]],
        s"${left.id} is not visible",
        s"${left.id} is visible"
      )
  }

  implicit class VisibleWorkOps(val work: Work.Visible[Identified]) {
    def singleImage: ImageData[IdState.Identified] =
      work.data.imageData.head
  }

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
