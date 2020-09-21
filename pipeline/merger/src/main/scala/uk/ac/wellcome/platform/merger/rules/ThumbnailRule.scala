package uk.ac.wellcome.platform.merger.rules

import scala.util.Try
import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{
  WorkPredicate,
  WorkPredicateOps
}
import WorkState.Unidentified

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
  type FieldData = Option[LocationDeprecated]

  override def merge(
    target: Work.Visible[Unidentified],
    sources: Seq[Work[Unidentified]]): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getThumbnail(target, sources),
      sources = List(
        getMetsThumbnail,
        getMinMiroThumbnail
      ).flatMap { rule =>
        rule.mergedSources(target, sources)
      }.distinct
    )

  def getThumbnail(
    target: Work.Visible[Unidentified],
    sources: Seq[Work[Unidentified]]): Option[LocationDeprecated] =
    if (shouldSuppressThumbnail(target, sources))
      None
    else
      getMetsThumbnail(target, sources)
        .orElse(getMinMiroThumbnail(target, sources))
        .getOrElse(target.data.thumbnailDeprecated)

  val getMetsThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
      val isDefinedForSource: WorkPredicate =
        WorkPredicates.singleDigitalItemMetsWork

      def rule(target: Work.Visible[Unidentified],
               sources: NonEmptyList[Work[Unidentified]]): FieldData = {
        debug(s"Choosing METS thumbnail from ${describeWork(sources.head)}")
        sources.head.data.thumbnailDeprecated
      }
    }

  val getMinMiroThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate =
        WorkPredicates.singleItemSierra or WorkPredicates.zeroItemSierra
      val isDefinedForSource: WorkPredicate =
        WorkPredicates.singleDigitalItemMiroWork

      def rule(target: Work.Visible[Unidentified],
               sources: NonEmptyList[Work[Unidentified]]): FieldData = {
        val minMiroSource = Try(sources.toList.min(MiroIdOrdering)).toOption
        minMiroSource.foreach { source =>
          debug(s"Choosing Miro thumbnail from ${describeWork(source)}")
        }
        minMiroSource.flatMap(_.data.thumbnailDeprecated)
      }

      object MiroIdOrdering extends Ordering[Work[Unidentified]] {
        def compare(x: Work[Unidentified], y: Work[Unidentified]): Int =
          (
            x.sourceIdentifier.identifierType.id,
            y.sourceIdentifier.identifierType.id) match {
            case ("miro-image-number", "miro-image-number") =>
              x.sourceIdentifier.value compare y.sourceIdentifier.value
            case _ => 0
          }
      }
    }

  def shouldSuppressThumbnail(target: Work.Visible[Unidentified],
                              sources: Seq[Work[Unidentified]]) =
    (target :: sources.toList).exists { work =>
      work.data.items.exists { item =>
        item.locationsDeprecated.exists(_.isRestrictedOrClosed)
      }
    }
}
