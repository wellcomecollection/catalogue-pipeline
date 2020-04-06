package uk.ac.wellcome.platform.merger.services

import cats.data.State

import uk.ac.wellcome.models.work.internal.{
  IdentifiableRedirect,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules._
import uk.ac.wellcome.platform.merger.rules.CalmRules._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}

/*
 * The implementor of a Merger must provide:
 * - `findTarget`, which finds the target from the input works
 * - `createMergeResult`, a recipe for creating a merged target and a complete
 *    list of works that should be redirected as a result of any merged fields.
 *
 * Calling `merge` with a list of works will return a new list of works including:
 * - the target work with all fields merged
 * - all redirected sources
 * - any other works untouched
 */
trait Merger extends MergerLogging {
  type MergeState = State[Set[TransformedBaseWork], MergeResult]

  protected def findTarget(
    works: Seq[TransformedBaseWork]): Option[UnidentifiedWork]

  protected def createMergeResult(target: UnidentifiedWork,
                                  sources: Seq[TransformedBaseWork]): MergeState

  protected def getTargetAndSources(works: Seq[TransformedBaseWork])
    : Option[(UnidentifiedWork, Seq[TransformedBaseWork])] =
    works match {
      case List(unmatchedWork: UnidentifiedWork) => Some((unmatchedWork, Nil))
      case matchedWorks =>
        findTarget(matchedWorks).map { target =>
          (
            target,
            matchedWorks.filterNot(
              _.sourceIdentifier == target.sourceIdentifier
            )
          )
        }
    }

  def merge(works: Seq[TransformedBaseWork]): MergerOutcome =
    getTargetAndSources(works)
      .map {
        case (target, sources) =>
          logIntentions(target, sources)
          val (mergeResultSources, result) = createMergeResult(target, sources)
            .run(Set.empty)
            .value

          val remaining = sources.toSet -- mergeResultSources
          val redirects = mergeResultSources.map(redirectSourceToTarget(target))
          logResult(result, redirects.toList, remaining.toList)

          MergerOutcome(
            works = redirects.toList ++ remaining :+ result.mergedTarget,
            images = result.images
          )
      }
      .getOrElse(MergerOutcome(works, Nil))

  private def redirectSourceToTarget(target: UnidentifiedWork)(
    source: TransformedBaseWork): UnidentifiedRedirectedWork =
    UnidentifiedRedirectedWork(
      version = source.version,
      sourceIdentifier = source.sourceIdentifier,
      redirect = IdentifiableRedirect(target.sourceIdentifier)
    )

  private def logIntentions(target: UnidentifiedWork,
                            sources: Seq[TransformedBaseWork]): Unit =
    sources match {
      case Nil =>
        info(s"Processing ${describeWork(target)}")
      case _ =>
        info(s"Attempting to merge ${describeMergeSet(target, sources)}")
    }

  private def logResult(result: MergeResult,
                        redirects: Seq[UnidentifiedRedirectedWork],
                        remaining: Seq[TransformedBaseWork]): Unit = {
    if (redirects.nonEmpty) {
      info(
        s"Merged ${describeMergeOutcome(result.mergedTarget, redirects, remaining)}")
    }
    if (result.images.nonEmpty) {
      info(s"Created images ${describeImages(result.images)}")
    }
  }

}

object PlatformMerger extends Merger {
  override def findTarget(
    works: Seq[TransformedBaseWork]): Option[UnidentifiedWork] =
    works
      .find(WorkPredicates.calmWork)
      .orElse(works.find(WorkPredicates.physicalSierra))
      .orElse(works.find(WorkPredicates.sierraWork)) match {
      case Some(work: UnidentifiedWork) => Some(work)
      case _                            => None
    }

  override def createMergeResult(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): MergeState =
    if (sources.isEmpty)
      State.pure(
        MergeResult(target, images = ImagesRule.merge(target).data)
      )
    else
      for {
        items <- ItemsRule(target, sources)
        thumbnail <- ThumbnailRule(target, sources)
        otherIdentifiers <- OtherIdentifiersRule(target, sources)
        images <- ImagesRule(target, sources)
        title <- TitleRule(target, sources)
        workType <- WorkTypeRule(target, sources)
        collectionPath <- CollectionPathRule(target, sources)
        physicalDescription <- PhysicalDescriptionRule(target, sources)
        contributors <- ContributorsRule(target, sources)
        subjects <- SubjectsRule(target, sources)
        language <- LanguageRule(target, sources)
        notes <- NotesRule(target, sources)
      } yield
        MergeResult(
          mergedTarget = target withData { data =>
            data.copy(
              items = items,
              thumbnail = thumbnail,
              otherIdentifiers = otherIdentifiers,
              images = images.map(_.toUnmerged),
              title = title,
              workType = workType,
              collectionPath = collectionPath,
              physicalDescription = physicalDescription,
              contributors = contributors,
              subjects = subjects,
              language = language,
              notes = notes,
              merged = true
            )
          },
          images = images
        )
}
