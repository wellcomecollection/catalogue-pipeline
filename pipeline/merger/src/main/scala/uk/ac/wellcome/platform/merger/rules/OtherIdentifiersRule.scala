package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

/*
 * The otherIdentifiers field is made up of all of the identifiers on the sources:
 * except for any Sierra identifiers on Miro sources, as these may be incorrect.
 *
 * - Calm IDs
 * - Miro IDs
 * - Digital sierra IDs
 */
object OtherIdentifiersRule extends FieldMergeRule with MergerLogging {
  type FieldData = List[SourceIdentifier]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] = {

    val rules = List(miroIdsRule, physicalDigitalIdsRule, calmIdsRule)
    val ids = rules.flatMap(_(target, sources)).flatten
    val mergeSources = sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    }

    FieldMergeResult(
      data = ids.distinct match {
        case Nil          => target.otherIdentifiers
        case nonEmptyList => nonEmptyList
      },
      sources = mergeSources
    )
  }

  private final val unmergeableMiroIdTypes =
    List("sierra-identifier", "sierra-system-number")

  private val miroIdsRule = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.singleItemSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.singleItemMiro

    override def rule(
      target: UnidentifiedWork,
      sources: NonEmptyList[TransformedBaseWork]): List[SourceIdentifier] = {
      debug(s"Merging Miro IDs from ${describeWorks(sources.toList)}")
      target.data.otherIdentifiers ++ sources.toList.flatMap(
        _.identifiers.filterNot(sourceIdentifier =>
          unmergeableMiroIdTypes.contains(sourceIdentifier.identifierType.id)))
    }
  }

  private val physicalDigitalIdsRule = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.physicalSierra
    val isDefinedForSource: WorkPredicate = WorkPredicates.digitalSierra

    override def rule(
      target: UnidentifiedWork,
      sources: NonEmptyList[TransformedBaseWork]): List[SourceIdentifier] = {
      debug(s"Merging physical and digital IDs from ${describeWorks(sources)}")
      target.data.otherIdentifiers ++ sources.toList.flatMap(_.identifiers)
    }
  }

  private val calmIdsRule = new PartialRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraWork
    val isDefinedForSource: WorkPredicate = WorkPredicates.calmWork

    override def rule(
      target: UnidentifiedWork,
      sources: NonEmptyList[TransformedBaseWork]): List[SourceIdentifier] = {
      debug(s"Merging physical and digital IDs from ${describeWorks(sources)}")
      target.data.otherIdentifiers ++ sources.toList
        .flatMap(_.identifiers)
        .filterNot(_.identifierType == IdentifierType("sierra-system-number"))
    }
  }
}
