package uk.ac.wellcome.platform.merger.rules

import scala.util.Try
import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal.{
  Location,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

/*
 * Thumbnails are chosen preferentially off of METS works, falling back to
 * Miro. If there are multiple Miro sources, the one with the lexicographically
 * minimal ID is chosen.
 *
 * If any of the locations forming the work from any source are marked as
 * restricted or closed, we supress the thumbnail to be sure we are not
 * displaying something we are not meant to.
 */
object ThumbnailRule extends FieldMergeRule with MergerLogging {
  type FieldData = Option[Location]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getThumbnail(target, sources),
      sources = List(getMetsThumbnail, getMinMiroThumbnail)
        .flatMap(_.mergedSources(target, sources))
        .distinct
    )

  def getThumbnail(target: UnidentifiedWork,
                   sources: Seq[TransformedBaseWork]): Option[Location] =
    if (shouldSuppressThumbnail(target, sources))
      None
    else
      getMetsThumbnail(target, sources)
        .orElse(getMinMiroThumbnail(target, sources))
        .getOrElse(target.data.thumbnail)

  val getMetsThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
      val isDefinedForSource: WorkPredicate =
        WorkPredicates.singleDigitalItemMetsWork

      def rule(target: UnidentifiedWork,
               sources: NonEmptyList[TransformedBaseWork]): FieldData = {
        debug(s"Choosing METS thumbnail from ${describeWork(sources.head)}")
        sources.head.data.thumbnail
      }
    }

  val getMinMiroThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
      val isDefinedForSource: WorkPredicate =
        WorkPredicates.singleDigitalItemMiroWork

      def rule(target: UnidentifiedWork,
               sources: NonEmptyList[TransformedBaseWork]): FieldData = {
        val minMiroSource = Try(sources.toList.min(MiroIdOrdering)).toOption
        minMiroSource.foreach { source =>
          debug(s"Choosing Miro thumbnail from ${describeWork(source)}")
        }
        minMiroSource.flatMap(_.data.thumbnail)
      }

      object MiroIdOrdering extends Ordering[TransformedBaseWork] {
        def compare(x: TransformedBaseWork, y: TransformedBaseWork): Int =
          (
            x.sourceIdentifier.identifierType.id,
            y.sourceIdentifier.identifierType.id) match {
            case ("miro-image-number", "miro-image-number") =>
              x.sourceIdentifier.value compare y.sourceIdentifier.value
            case _ => 0
          }
      }
    }

  def shouldSuppressThumbnail(target: UnidentifiedWork,
                              sources: Seq[TransformedBaseWork]) =
    (target :: sources.toList).exists { work =>
      work.data.items.exists { item =>
        item.locations.exists(_.isRestrictedOrClosed)
      }
    }
}
