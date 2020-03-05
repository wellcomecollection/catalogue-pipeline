package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  Location,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

import scala.util.Try

/*
 * Thumbnails are chosen preferentially off of METS works, falling back to
 * Miro. If there are multiple Miro sources, the one with the lexicographically
 * minimal ID is chosen.
 */
object ThumbnailRule extends FieldMergeRule with MergerLogging {
  type FieldData = Option[Location]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): MergeResult[FieldData] =
    MergeResult(
      fieldData = (getMetsThumbnail orElse getMinMiroThumbnail orElse
        (identityOnTarget andThen (_.data.thumbnail)))((target, sources)),
      redirects = Nil
    )

  private val getMetsThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
      val isDefinedForSource: WorkPredicate =
        WorkPredicates.singleItemDigitalMets

      def rule(target: UnidentifiedWork,
               sources: Seq[TransformedBaseWork]): FieldData = {
        debug(s"Choosing METS thumbnail from ${describeWorks(sources)}")
        sources.headOption.flatMap(_.data.thumbnail)
      }
    }

  private val getMinMiroThumbnail =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
      val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemMiro

      def rule(target: UnidentifiedWork,
               sources: Seq[TransformedBaseWork]): FieldData = {
        val minMiroSource = Try(sources.min(MiroIdOrdering)).toOption
        minMiroSource.foreach { source =>
          debug(s"Choosing METS thumbnail from ${describeWork(source)}")
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
}
