package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifiableRedirect,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.{
  ItemsRule,
  MergeResult,
  OtherIdentifiersRule,
  ThumbnailRule,
  WorkPredicates
}
import cats.data.State
import uk.ac.wellcome.platform.merger.logging.MergerLogging

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
    sources: Seq[TransformedBaseWork]): RedirectsAccumulator[UnidentifiedWork]

  protected def getTargetAndSources(works: Seq[TransformedBaseWork])
    : Option[(UnidentifiedWork, Seq[TransformedBaseWork])] =
    findTarget(works).map { target =>
      (target, works.filterNot(_.sourceIdentifier == target.sourceIdentifier))
    }

  def merge(works: Seq[TransformedBaseWork]): Seq[BaseWork] =
    getTargetAndSources(works)
      .map {
        case (target, sources) =>
          info(s"Attempting to merge ${describeMergeSet(target, sources)}")
          val (toRedirect, mergedTarget) = createMergeResult(target, sources)
            .run(Set.empty)
            .value
          val remaining = sources.toSet -- toRedirect
          val redirects = toRedirect.map(redirectSourceToTarget(target))
          info(
            s"Merged ${describeMergeOutcome(target, redirects.toList, remaining.toList)}")
          redirects.toList ++ remaining :+ mergedTarget
      }
      .getOrElse(works)

  protected def accumulateRedirects[T](
    merged: => MergeResult[T]): RedirectsAccumulator[T] =
    merged match {
      case MergeResult(field, ruleRedirects) =>
        State(existingRedirects =>
          (existingRedirects ++ ruleRedirects.toSet, field))
    }

  def redirectSourceToTarget(target: UnidentifiedWork)(
    source: TransformedBaseWork): UnidentifiedRedirectedWork =
    UnidentifiedRedirectedWork(
      version = source.version,
      sourceIdentifier = source.sourceIdentifier,
      redirect = IdentifiableRedirect(target.sourceIdentifier)
    )
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
    sources: Seq[TransformedBaseWork]): RedirectsAccumulator[UnidentifiedWork] =
    for {
      items <- accumulateRedirects(ItemsRule.merge(target, sources))
      thumbnail <- accumulateRedirects(ThumbnailRule.merge(target, sources))
      otherIdentifiers <- accumulateRedirects(
        OtherIdentifiersRule.merge(target, sources))
    } yield
      target withData { data =>
        data.copy(
          items = items,
          thumbnail = thumbnail,
          otherIdentifiers = otherIdentifiers,
          merged = true
        )
      }
}
