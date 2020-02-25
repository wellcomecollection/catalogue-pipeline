package uk.ac.wellcome.platform.merger.rules
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.WorkFilters.WorkFilter

/*
 * MergeResult is the return type of a FieldMergeRule's `merge` method
 * and contains both the new (merged) value for the field but also a list
 * of sources which the rule would like the merger to redirect. It is up to
 * the merger how to handle these.
 */
case class MergeResult[T](fieldData: T, redirects: Seq[TransformedBaseWork])

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

  def merge(target: UnidentifiedWork,
            sources: Seq[TransformedBaseWork]): MergeResult[FieldData]

  protected val identityOnTarget: PartialFunction[Params, UnidentifiedWork] = {
    case (target, _) => target
  }

  protected trait PartialRule extends PartialFunction[Params, FieldData] {
    val isDefinedForTarget: WorkFilter
    val isDefinedForSource: WorkFilter

    def rule(target: UnidentifiedWork,
             sources: Seq[TransformedBaseWork]): FieldData

    override def apply(params: Params): FieldData =
      params match {
        case (target, sources) =>
          rule(target, sources.filter(isDefinedForSource))
      }

    override def isDefinedAt(params: Params): Boolean = params match {
      case (target, sources) =>
        isDefinedForTarget(target) && sources.exists(isDefinedForSource)
    }
  }

  /*
   * PartialRules can be used with `orElse` as a chain of fallbacks for picking a
   * field off sources, or they can be composed.
   *
   * `composeRules` is takes a list of rules, as well as a helper function to
   * copy the new merged Fields from each step into the "running-total" target,
   * and composes them right-to-left.
   */
  protected final def composeRules(
    liftIntoTarget: UnidentifiedWork => FieldData => UnidentifiedWork)(
    rules: PartialRule*)(target: UnidentifiedWork,
                         sources: Seq[TransformedBaseWork]): UnidentifiedWork =
    rules.foldRight(target) { (applyRule, nextTarget) =>
      (applyRule andThen liftIntoTarget(nextTarget) orElse identityOnTarget)(
        (nextTarget, sources))
    }
}
