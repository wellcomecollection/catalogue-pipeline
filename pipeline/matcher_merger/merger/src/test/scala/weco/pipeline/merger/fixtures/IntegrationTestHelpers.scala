package weco.pipeline.merger.fixtures

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda.{Downstream, SQSLambdaMessageFailure, SQSLambdaMessageResult}
import weco.lambda.helpers.LambdaFixtures
import weco.pipeline.matcher.MatcherSQSLambda
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.matcher.StoredWorksMatcher
import weco.pipeline.matcher.models.{MatcherResult, WorkStub}
import weco.pipeline.merger.config.{MergerConfig, MergerConfigurable}
import weco.pipeline.merger.{MergeProcessor, MergerSQSLambda}
import weco.pipeline_storage.{Retriever, RetrieverMultiResult}
import weco.pipeline_storage.memory.MemoryRetriever

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import weco.fixtures.TestWith

/** Wiring for the matcher/merger integration tests, kept out of the tests so
  * they stay a readable description of the behaviour we want. See the README
  * for what is real here and what is faked.
  */
trait IntegrationTestHelpers
    extends EitherValues
    with ScalaFutures
    with LambdaFixtures
    with MatcherFixtures
    with MergerFixtures
    with WorkGenerators {

  case class IntegrationMatcherLambda(
    worksMatcher: StoredWorksMatcher,
    downstream: Downstream
  ) extends MatcherSQSLambda[MatcherConfig]
      with MatcherConfigurable

  case class IntegrationMergerLambda(
    mergeProcessor: MergeProcessor,
    imageMsgSender: MemorySNSDownstream
  ) extends MergerSQSLambda[MergerConfig]
      with MergerConfigurable

  type IdentifiedIndex = MemoryRetriever[Work[WorkState.Identified]]
  type MergedIndex = mutable.Map[String, WorkOrImage]

  // Presenting the shared index as stubs keeps one copy of each work in a test,
  // matching the way the matcher reads stubs from the identified index.
  private class WorkStubIndex(identifiedIndex: IdentifiedIndex)
      extends Retriever[WorkStub] {
    override implicit val ec: ExecutionContext = global

    override def apply(
      ids: Seq[String]
    ): Future[RetrieverMultiResult[WorkStub]] =
      identifiedIndex(ids).map {
        result =>
          RetrieverMultiResult(
            found = result.found.map {
              case (id, work) => id -> WorkStub(work)
            },
            notFound = result.notFound
          )
      }(global)
  }

  case class Context(
    matcher: IntegrationMatcherLambda,
    merger: IntegrationMergerLambda,
    imageDownstream: MemorySNSDownstream,
    matcherDownstream: MemorySNSDownstream,
    identifiedIndex: IdentifiedIndex,
    mergedIndex: MergedIndex
  ) {
    def getMerged(
      originalWork: Work[WorkState.Identified]
    ): Work[WorkState.Merged] =
      mergedIndex(originalWork.state.canonicalId.underlying).left.value

    def imageData: Seq[ImageData[IdState.Identified]] =
      mergedIndex.values.collect {
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

  implicit class VisibleWorkOps(val work: Work.Visible[Identified]) {
    def singleImage: ImageData[IdState.Identified] =
      work.data.imageData.head
  }

  def withContext[R](testWith: TestWith[Context, R]): R =
    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            val identifiedIndex: IdentifiedIndex =
              new MemoryRetriever[Work[WorkState.Identified]]()
            val workStubIndex = new WorkStubIndex(identifiedIndex)

            withWorkMatcher(workGraphStore, workStubIndex) {
              workMatcher =>
                val mergedIndex = mutable.Map[String, WorkOrImage]()
                val matcherDownstream = new MemorySNSDownstream
                val imageDownstream = new MemorySNSDownstream

                val matcher = IntegrationMatcherLambda(
                  new StoredWorksMatcher(workStubIndex, workMatcher),
                  matcherDownstream
                )

                val merger =
                  withMergerProcessor(identifiedIndex, mergedIndex) {
                    mergeProcessor =>
                      IntegrationMergerLambda(mergeProcessor, imageDownstream)
                  }

                testWith(
                  Context(
                    matcher = matcher,
                    merger = merger,
                    imageDownstream = imageDownstream,
                    matcherDownstream = matcherDownstream,
                    identifiedIndex = identifiedIndex,
                    mergedIndex = mergedIndex
                  )
                )
            }
        }
    }

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

  def beVisible: Matcher[Work[Merged]] =
    (left: Work[Merged]) =>
      MatchResult(
        left.isInstanceOf[Work.Visible[Merged]],
        s"${left.id} is not visible",
        s"${left.id} is visible"
      )

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

  def processWorks(
    works: Work[WorkState.Identified]*
  )(implicit context: Context): Unit =
    works.foreach(processWork)

  /** Put a work into the index and take it through the matcher and then the
    * merger, as the pipeline does once a work has been identified.
    */
  def processWork(
    work: Work[WorkState.Identified]
  )(implicit context: Context): Unit = {
    context.identifiedIndex.index +=
      (work.state.canonicalId.underlying -> work)

    val imagesBefore = indexedImageIds
    val notificationsBefore = context.imageDownstream.msgSender.messages.size

    runMerger(runMatcher(work))

    assertImagesNotified(imagesBefore, notificationsBefore)
  }

  private def runMatcher(
    work: Work[WorkState.Identified]
  )(implicit context: Context): Seq[MatcherResult] = {
    val alreadySent =
      context.matcherDownstream.msgSender.getMessages[MatcherResult].size

    whenReady(
      context.matcher.processMessages(
        messages = Seq(
          SQSTestLambdaMessage(message = work.state.canonicalId.underlying)
        )
      )
    ) {
      results => assertNoFailures("matcher", results)
    }

    context.matcherDownstream.msgSender
      .getMessages[MatcherResult]
      .drop(alreadySent)
  }

  private def runMerger(
    matcherResults: Seq[MatcherResult]
  )(implicit context: Context): Unit =
    whenReady(
      context.merger.processMessages(
        messages = matcherResults.map(
          matcherResult => SQSTestLambdaMessage(message = matcherResult)
        )
      )
    ) {
      results => assertNoFailures("merger", results)
    }

  private def assertNoFailures(
    application: String,
    results: Seq[SQSLambdaMessageResult]
  ): Unit = {
    val failures = results.collect {
      case failure: SQSLambdaMessageFailure => failure.error
    }

    assert(
      failures.isEmpty,
      s"The $application failed to process a message: ${failures.mkString(", ")}"
    )
  }

  private def indexedImageIds(implicit context: Context): Set[String] =
    context.mergedIndex.values.collect { case Right(image) => image.id }.toSet

  /** Only images result in a notification; works are picked up downstream by a
    * window-based read of the merged index.
    *
    * Compares what this pass did rather than the whole scenario, so an image
    * that was notified on an earlier pass can't cover for a later one.
    */
  private def assertImagesNotified(
    imagesBefore: Set[String],
    notificationsBefore: Int
  )(implicit context: Context): Unit = {
    val newlyIndexed = indexedImageIds -- imagesBefore
    val newlyNotifiedBodies = context.imageDownstream.msgSender.messages
      .drop(notificationsBefore)
      .map(_.body)
    val newlyNotified = newlyNotifiedBodies.toSet

    val duplicates = newlyNotifiedBodies
      .groupBy(identity)
      .collect { case (body, occurrences) if occurrences.size > 1 => body }

    assert(
      duplicates.isEmpty,
      s"Images ${duplicates.mkString(", ")} were sent downstream more than once in a single pass"
    )

    assert(
      newlyIndexed.subsetOf(newlyNotified),
      s"Images ${(newlyIndexed -- newlyNotified).mkString(", ")} were saved but not sent downstream"
    )

    assert(
      newlyNotified.subsetOf(indexedImageIds),
      s"Images ${(newlyNotified -- indexedImageIds).mkString(", ")} were sent downstream but never saved"
    )
  }
}
