package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  SourceIdentifier,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
import scala.Function.const

/*
 * The otherIdentifiers field is made up of all of the identifiers on the sources,
 * except for any Sierra identifiers on Miro sources, as these may be incorrect
 */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {
    val miroIds =
      miroIdsRule.applyOrElse((target, sources), const(Nil))
    val physicalDigitalIds =
      physicalDigitalIdsRule.applyOrElse((target, sources), const(Nil))
    FieldMergeResult(
      fieldData = (physicalDigitalIds ++ miroIds).distinct match {
        case nonEmptyList @ _ :: _ => nonEmptyList
        case Nil                   => target.otherIdentifiers
      },
      redirects = sources.filter { source =>
        (miroIdsRule orElse physicalDigitalIdsRule)
          .isDefinedAt((target, List(source)))
      }
    )
  }

  private final val unmergeableMiroIdTypes =
    List("sierra-identifier", "sierra-system-number")
  private lazy val miroIdsRule = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemMiro

    override def rule(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): List[SourceIdentifier] = {
      info(s"Merging Miro IDs from ${describeWorks(sources)}")
      target.data.otherIdentifiers ++ sources.flatMap(
        _.identifiers.filterNot(sourceIdentifier =>
          unmergeableMiroIdTypes.contains(sourceIdentifier.identifierType.id)))
    }
  }

  private lazy val physicalDigitalIdsRule = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.physicalSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.digitalSierra

    override def rule(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): List[SourceIdentifier] = {
      info(s"Merging physical and digital IDs from ${describeWorks(sources)}")
      target.data.otherIdentifiers ++ sources.flatMap(_.identifiers)
    }
  }
}
