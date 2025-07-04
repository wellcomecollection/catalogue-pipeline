package weco.pipeline.merger.fixtures

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda.Downstream
import weco.lambda.helpers.LambdaFixtures
import weco.pipeline.matcher.MatcherSQSLambda
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.matcher.WorksMatcher
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline.merger.config.{MergerConfig, MergerConfigurable}
import weco.pipeline.merger.{MergeProcessor, MergerSQSLambda}
import weco.pipeline_storage.memory.MemoryRetriever

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.duration._
import weco.fixtures.TestWith

// These are in a separate file to avoid cluttering up the integration tests
// with code that doesn't tell us about the desired matcher/merger behaviour.
trait IntegrationTestHelpers
    extends EitherValues
    with ScalaFutures
    with LambdaFixtures
    with MatcherFixtures
    with MergerFixtures
    with WorkGenerators {

  case class StubMatcherLambda(
    worksMatcher: WorksMatcher,
    downstream: Downstream
  ) extends MatcherSQSLambda[MatcherConfig]
    with MatcherConfigurable

  case class StubMergerLambda(
    mergeProcessor: MergeProcessor,
    workRouter: MemoryWorkRouter,
    imageMsgSender: MemorySNSDownstream
  ) extends MergerSQSLambda[MergerConfig]
    with MergerConfigurable


  type MatcherDownstream = MemorySNSDownstream
  type ImageDownstream = MemorySNSDownstream
  type IdentifiedIndex = MemoryRetriever[Work[WorkState.Identified]]
  type MergedIndex = mutable.Map[String, WorkOrImage]

  type Context = (MatcherStub, StubMatcherLambda, StubMergerLambda, ImageDownstream, MatcherDownstream, IdentifiedIndex, MergedIndex)

  implicit class ContextOps(context: Context) {
    val (_, _, _, _, _, _, mergedIndex) = context

    def getMerged(
                   originalWork: Work[WorkState.Identified]
                 ): Work[WorkState.Merged] = {

      mergedIndex(originalWork.state.canonicalId.underlying).left.value
    }

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

  def withContext[R](testWith: TestWith[Context, R]): R = {
    val mergedIndex = mutable.Map[String, WorkOrImage]()
    val identifiedIndex: MemoryRetriever[Work[WorkState.Identified]] =
      new MemoryRetriever[Work[WorkState.Identified]]()

    val matcherDownstream = new MemorySNSDownstream
    val imageSender: MemorySNSDownstream = new MemorySNSDownstream

    val matcherStub = MatcherStub()
    val matcher = StubMatcherLambda(matcherStub, matcherDownstream)

    val merger: StubMergerLambda = withMergerProcessor(identifiedIndex, mergedIndex) {
      mergeProcessor => {
        StubMergerLambda(mergeProcessor, workRouter, imageSender)
      }
    }

    val context = (matcherStub, matcher, merger, imageSender, matcherDownstream, identifiedIndex, mergedIndex)

    testWith(context)
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

  def beVisible: Matcher[Work[Merged]] = (left: Work[Merged]) => MatchResult(
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
  )(implicit context: Context): Unit = {
    val (matcherStub, matcher, merger, imageSender, matcherDownstream, identifiedIndex, mergedIndex) = context

    works.foreach {
      w =>
        println(
          s"Processing work ${w.state.sourceIdentifier} (${w.state.canonicalId})"
        )
        identifiedIndex.index ++= Map(w.state.canonicalId.underlying -> w)
    }

    val canonicalIds = works.map(_.state.canonicalId.underlying).toSet

    // Append the canonical IDs to the matcher stub so that it can return
    // the expected results when it processes the messages.
    matcherStub.setShorthandResults(Seq(Set(canonicalIds)))

    whenReady(
      matcher.processMessages(messages =
        works.map {
          work =>  SQSTestLambdaMessage(message = work.state.canonicalId.underlying)
        }
      )
    ) {
      _ => val matcherResults = matcherDownstream.msgSender
        .getMessages[MatcherResult]
        whenReady(
          merger.processMessages(messages =
            matcherResults.map(
              matcherResult => {
                SQSTestLambdaMessage(message = MatcherResult.encoder(matcherResult).toString)
              }
            )
          )
        ) {
          // Check that the merger has notified the next application about everything
          // in the index.  This check could be more robust, but it'll do for now.
          _ =>
            val idsSentByTheMerger = (
                workRouter.workSender.messages ++
                workRouter.pathSender.messages ++
                workRouter.pathConcatenatorSender.messages ++
                imageSender.msgSender.messages
              ).map(_.body).toSet
            mergedIndex.keySet.size == idsSentByTheMerger.size
        }
    }
  }

  def processWork(work: Work[WorkState.Identified])(implicit context: Context): Unit = processWorks(work)(context)
}
