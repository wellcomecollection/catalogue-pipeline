package weco.pipeline.merger.rules

import cats.data.{NonEmptyList, State}
import weco.catalogue.internal_model.work.WorkState.Identified
import WorkPredicates.WorkPredicate
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.models.FieldMergeResult

import scala.Function.const

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
    (Work.Visible[Identified], Seq[Work[Identified]])
  protected type FieldData
  protected type MergeState = State[Set[Work[Identified]], FieldData]

  def apply(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData] =
    merge(target, sources)

  def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): FieldMergeResult[FieldData]

  protected trait PartialRule {
    val isDefinedForTarget: WorkPredicate
    val isDefinedForSource: WorkPredicate
    val isDefinedForSourceList: Seq[Work[Identified]] => Boolean =
      const(true)

    protected def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): FieldData

    def apply(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): Option[FieldData] =
      mergedSources(target, sources) match {
        case head +: tail =>
          Some(rule(target, NonEmptyList(head, tail.toList)))
        case _ => None
      }

    def apply(
      target: Work.Visible[Identified],
      source: Work[Identified]
    ): Option[FieldData] =
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
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): Seq[Work[Identified]] =
      if (isDefinedForSourceList(sources) && isDefinedForTarget(target)) {
        sources.filter(isDefinedForSource)
      } else {
        Nil
      }
  }
}
