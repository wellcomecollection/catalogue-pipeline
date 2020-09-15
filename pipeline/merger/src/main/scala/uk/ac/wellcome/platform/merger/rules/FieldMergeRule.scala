package uk.ac.wellcome.platform.merger.rules

import scala.Function.const
import cats.data.{NonEmptyList, State}

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.WorkPredicate
import WorkState.Unidentified

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
  protected final type Params =
    (Work.Standard[Unidentified], Seq[Work[Unidentified]])
  protected type FieldData
  protected type MergeState = State[Set[Work[Unidentified]], FieldData]

  def apply(target: Work.Standard[Unidentified],
            sources: Seq[Work[Unidentified]]): MergeState =
    merge(target, sources) match {
      case FieldMergeResult(field, mergedSources) =>
        State(existingMergedSources =>
          (existingMergedSources ++ mergedSources.toSet, field))
    }

  def merge(target: Work.Standard[Unidentified],
            sources: Seq[Work[Unidentified]]): FieldMergeResult[FieldData]

  protected trait PartialRule {
    val isDefinedForTarget: WorkPredicate
    val isDefinedForSource: WorkPredicate
    val isDefinedForSourceList: Seq[Work[Unidentified]] => Boolean =
      const(true)

    protected def rule(target: Work.Standard[Unidentified],
                       sources: NonEmptyList[Work[Unidentified]]): FieldData

    def apply(target: Work.Standard[Unidentified],
              sources: Seq[Work[Unidentified]]): Option[FieldData] =
      mergedSources(target, sources) match {
        case head +: tail =>
          Some(rule(target, NonEmptyList(head, tail.toList)))
        case _ => None
      }

    def apply(target: Work.Standard[Unidentified],
              source: Work[Unidentified]): Option[FieldData] =
      apply(target, List(source))

    /*
     * This gets the list of sources that a partial rule can merge, given a
     * target and a complete list of sources.
     *
     * For this list to be non-empty the following must be satisfied:
     * - `isDefinedForTarget(target)` is `true`
     * - `isDefinedForSourceList(sources)` is `true`
     * - `isDefinedForSource(source)` is `true` for at least one element of `sources`
     */
    def mergedSources(
      target: Work.Standard[Unidentified],
      sources: Seq[Work[Unidentified]]): Seq[Work[Unidentified]] =
      if (isDefinedForSourceList(sources) && isDefinedForTarget(target)) {
        sources.filter(isDefinedForSource)
      } else {
        Nil
      }
  }
}
