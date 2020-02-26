package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.work.internal.{
  IdentifiableRedirect,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.{
  ImagesRule,
  ItemsRule,
  OtherIdentifiersRule,
  ThumbnailRule,
  WorkPredicates
}
import cats.data.State
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{
  FieldMergeResult,
  MergeResult,
  MergerOutcome
}

/*
 * The implementor of a Merger must provide:
 * - `findTarget`, which finds the target from the input works
 * - `createMergeResult`, a recipe for creating a merged target and a complete
 *    list of redirects from an input target and a list of sources.
 *
 * The redirects can be implicitly accumulated without duplication or mutation
 * and while preserving referential transparency by using the
 * `accumulateRedirects` helper.
 *
 * Calling `merge` with a list of works will return a new list of works including:
 * - the target work with all fields merged
 * - all redirected sources
 * - any other works untouched
 */
abstract class Merger extends MergerLogging {
  type RedirectsAccumulator[T] = State[Set[TransformedBaseWork], T]

  protected def findTarget(
    works: Seq[TransformedBaseWork]): Option[UnidentifiedWork]

  protected def createMergeResult(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): RedirectsAccumulator[MergeResult]

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
          val (toRedirect, result) = createMergeResult(target, sources)
            .run(Set.empty)
            .value
          val remaining = sources.toSet -- toRedirect
          val redirects = toRedirect.map(redirectSourceToTarget(target))
          logResult(result, redirects.toList, remaining.toList)

          MergerOutcome(
            works = redirects.toList ++ remaining :+ result.target,
            images = result.images
          )
      }
      .getOrElse(MergerOutcome(works, Nil))

  protected def accumulateRedirects[T](
    merged: => FieldMergeResult[T]): RedirectsAccumulator[T] =
    merged match {
      case FieldMergeResult(field, ruleRedirects) =>
        State(existingRedirects =>
          (existingRedirects ++ ruleRedirects.toSet, field))
    }

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
        s"Merged ${describeMergeOutcome(result.target, redirects, remaining)}")
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
      .find(WorkPredicates.physicalSierra)
      .orElse(works.find(WorkPredicates.sierraWork)) match {
      case Some(target: UnidentifiedWork) => Some(target)
      case _                              => None
    }

  override def createMergeResult(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): RedirectsAccumulator[MergeResult] =
    sources match {
      case Nil =>
        State.pure(
          MergeResult(target, images = ImagesRule.merge(target, Nil).fieldData)
        )
      case _ =>
        for {
          items <- accumulateRedirects(ItemsRule.merge(target, sources))
          thumbnail <- accumulateRedirects(ThumbnailRule.merge(target, sources))
          otherIdentifiers <- accumulateRedirects(
            OtherIdentifiersRule.merge(target, sources))
          images <- accumulateRedirects(ImagesRule.merge(target, sources))
        } yield
          MergeResult(
            target = target withData { data =>
              data.copy(
                items = items,
                thumbnail = thumbnail,
                otherIdentifiers = otherIdentifiers,
                images = images.map(_.toUnmerged),
                merged = true
              )
            },
            images = images
          )
    }

}
