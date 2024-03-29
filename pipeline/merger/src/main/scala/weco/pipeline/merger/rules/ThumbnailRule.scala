package weco.pipeline.merger.rules

import scala.util.Try
import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.FieldMergeResult

/*
 * Thumbnails are chosen preferentially off of METS works, falling back to
 * Miro. If there are multiple Miro sources, the one with the lexicographically
 * minimal ID is chosen.
 *
 * If any of the digital locations forming the work from any source are marked as
 * restricted or closed, we suppress the thumbnail to be sure we are not
 * displaying something we are not meant to.
 *
 * Physical location restrictions are ignored, as they are to do with the physical
 * item (e.g. because of fragility), rather than content.
 */
object ThumbnailRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._

  type FieldData = Option[DigitalLocation]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getThumbnail(target, sources),
      sources = List(
        getMetsThumbnail,
        getMinMiroThumbnail
      ).flatMap {
        rule =>
          rule.mergedSources(target, sources)
      }.distinct
    )

  private def getThumbnail(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Option[DigitalLocation] =
    if (shouldSuppressThumbnail(target, sources))
      None
    else
      getMetsThumbnail(target, sources)
        .orElse(getMinMiroThumbnail(target, sources))
        .getOrElse(target.data.thumbnail)

  private val getMetsThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate =
        sierraWork or singlePhysicalItemCalmWork or teiWork
      val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork

      def rule(
        target: Work.Visible[Identified],
        sources: NonEmptyList[Work[Identified]]
      ): FieldData = {
        debug(s"Choosing METS thumbnail from ${describeWork(sources.head)}")
        sources.head.data.thumbnail
      }
    }

  private val getMinMiroThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate =
        singleItemSierra or zeroItemSierra or singlePhysicalItemCalmWork or teiWork
      val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork

      def rule(
        target: Work.Visible[Identified],
        sources: NonEmptyList[Work[Identified]]
      ): FieldData = {
        val minMiroSource = Try(sources.toList.min(MiroIdOrdering)).toOption
        minMiroSource.foreach {
          source =>
            debug(s"Choosing Miro thumbnail from ${describeWork(source)}")
        }
        minMiroSource.flatMap(_.data.thumbnail)
      }

      object MiroIdOrdering extends Ordering[Work[Identified]] {
        def compare(x: Work[Identified], y: Work[Identified]): Int =
          (
            x.sourceIdentifier.identifierType,
            y.sourceIdentifier.identifierType
          ) match {
            case (
                  IdentifierType.MiroImageNumber,
                  IdentifierType.MiroImageNumber
                ) =>
              x.sourceIdentifier.value compare y.sourceIdentifier.value
            case _ => 0
          }
      }
    }

  private def shouldSuppressThumbnail(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Boolean =
    (target :: sources.toList).exists {
      work =>
        work.data.items.exists {
          item =>
            item.locations.exists(
              location =>
                location.hasRestrictions && location
                  .isInstanceOf[DigitalLocation]
            )
        }
    }
}
