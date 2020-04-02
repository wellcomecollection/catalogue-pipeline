package uk.ac.wellcome.platform.merger.rules

import cats.data.{NonEmptyList, State}

import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate

/*
 * A trait to extend in order to merge fields of the type member `Field`
 *
 * The implementor must provide the method `merge`, which takes a target
 * work and a list of source works, and returns the new value of the field
 * as well as a list of source works that this rule would like to be redirected.
 *
 * Many merge rules will want to apply different logic given conditions on
 * both the target and the source works.
 * The PartialRule trait is provided to achieve this: in addition to
 * a `rule` that has the same signature as the `merge` method, the implementor
 * must provide predicates for valid targets and sources.
 *
 * Because PartialRule is a PartialFunction, the `rule` will never be called
 * for targets/sources that don't satisfy these predicates, and we gain things
 * like `orElse` and `andThen` for free.
 */
trait FieldMergeRule {
  protected final type Params = (UnidentifiedWork, Seq[TransformedBaseWork])
  protected type FieldData
  protected type MergeState = State[Set[TransformedBaseWork], FieldData]

  def apply(target: UnidentifiedWork,
            sources: Seq[TransformedBaseWork]): MergeState =
    merge(target, sources) match {
      case FieldMergeResult(field, mergedSources) =>
        State(existingMergedSources =>
          (existingMergedSources ++ mergedSources.toSet, field))
    }

  def merge(target: UnidentifiedWork,
            sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData]

  protected def getMergedSources(
    rules: List[PartialRule],
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork]): Seq[TransformedBaseWork] = {

    sources.filter { source =>
      rules.exists(_(target, source).isDefined)
    }
  }

  protected trait PartialRule {
    val isDefinedForTarget: WorkPredicate
    val isDefinedForSource: WorkPredicate

    protected def rule(target: UnidentifiedWork,
                       sources: NonEmptyList[TransformedBaseWork]): FieldData

    def apply(target: UnidentifiedWork,
              sources: Seq[TransformedBaseWork]): Option[FieldData] =
      (isDefinedForTarget(target), sources.filter(isDefinedForSource)) match {
        case (true, head +: tail) =>
          Some(rule(target, NonEmptyList(head, tail.toList)))
        case _ => None
      }

    def apply(target: UnidentifiedWork,
              source: TransformedBaseWork): Option[FieldData] =
      apply(target, List(source))
  }
}
