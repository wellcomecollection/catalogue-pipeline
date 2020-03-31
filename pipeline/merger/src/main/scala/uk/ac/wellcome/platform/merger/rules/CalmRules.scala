package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

/*
 * Rule used for fields where we want to always use the Calm data if it
 * exists.
 *
 * Implementations should provide a getData function which returns the
 * fields data when given the work.
 */
trait UseCalmWhenExistsRule extends FieldMergeRule with MergerLogging {

  protected val getData: TransformedBaseWork => FieldData

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = calmRule(target, sources) getOrElse getData(target),
      sources = Nil
    )

  val calmRule =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
      val isDefinedForSource: WorkPredicate = WorkPredicates.calmWork

      def rule(sierraWork: UnidentifiedWork,
               calmWorks: NonEmptyList[TransformedBaseWork]): FieldData =
        getData(calmWorks.head)
    }
}

object UseCalmWhenExistsRule {
  def apply[T](f: TransformedBaseWork => T) =
    new UseCalmWhenExistsRule {
      type FieldData = T
      protected val getData = f
    }
}

object CalmRules {

  val CollectionPathRule = UseCalmWhenExistsRule(_.data.collectionPath)

  val TitleRule = UseCalmWhenExistsRule(_.data.title)

  val PhysicalDescriptionRule = UseCalmWhenExistsRule(
    _.data.physicalDescription)

  val ContributorsRule = UseCalmWhenExistsRule(_.data.contributors)

  val SubjectsRule = UseCalmWhenExistsRule(_.data.subjects)

  val LanguageRule = UseCalmWhenExistsRule(_.data.language)

  val NotesRule = UseCalmWhenExistsRule(_.data.notes)

  val WorkTypeRule = UseCalmWhenExistsRule(_.data.workType)
}
