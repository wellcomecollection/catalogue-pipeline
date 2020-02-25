package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  SourceIdentifier,
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.rules.WorkFilters.WorkFilter

/*
 * The otherIdentifiers field is made up of all of the identifiers on the sources,
 * except for any Sierra identifiers on Miro sources, as these may be incorrect
 */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): MergeResult[FieldData] = {
    val empty = (_: Params) => Nil
    val miroIds =
      miroIdsRule.applyOrElse((target, sources), empty)
    val physicalDigitalIds =
      physicalDigitalIdsRule.applyOrElse((target, sources), empty)
    MergeResult(
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
    val isDefinedForTarget: WorkFilter = WorkFilters.singleItemSierra
    val isDefinedForSource: WorkFilter = WorkFilters.singleItemMiro

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
    val isDefinedForTarget: WorkFilter = WorkFilters.physicalSierra
    val isDefinedForSource: WorkFilter = WorkFilters.digitalSierra

    override def rule(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): List[SourceIdentifier] = {
      info(s"Merging physical and digital IDs from ${describeWorks(sources)}")
      target.data.otherIdentifiers ++ sources.flatMap(_.identifiers)
    }
  }
}
