package weco.catalogue.source_model.fixtures

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.locations.{AccessCondition, AccessMethod, AccessStatus}

trait AccessConditionMatchers {
  type AccessConditionMatcher[T] = HavePropertyMatcher[AccessCondition, T]

  private def createMatcher[T](
    propertyName: String, expectedValue: T, actualValue: (AccessCondition => T)
  ): AccessConditionMatcher[T] =
    (condition: AccessCondition) => HavePropertyMatchResult(
      matches = expectedValue == actualValue(condition),
      propertyName = propertyName,
      expectedValue = expectedValue,
      actualValue = actualValue(condition)
    )

  def method(method: AccessMethod): AccessConditionMatcher[AccessMethod] =
    createMatcher(
      propertyName = "method",
      expectedValue = method,
      actualValue = condition => condition.method
    )

  def status(status: AccessStatus): AccessConditionMatcher[Option[AccessStatus]] =
    createMatcher(
      propertyName = "status",
      expectedValue = Some(status),
      actualValue = condition => condition.status
    )

  def noStatus(): AccessConditionMatcher[Option[AccessStatus]] =
    createMatcher(
      propertyName = "status",
      expectedValue = None,
      actualValue = condition => condition.status
    )

  def terms(terms: String): AccessConditionMatcher[Option[String]] =
    createMatcher(
      propertyName = "terms",
      expectedValue = Some(terms),
      actualValue = condition => condition.terms
    )

  def noTerms(): AccessConditionMatcher[Option[String]] =
    createMatcher(
      propertyName = "terms",
      expectedValue = None,
      actualValue = condition => condition.terms
    )

  def note(note: String): AccessConditionMatcher[Option[String]] =
    createMatcher(
      propertyName = "note",
      expectedValue = Some(note),
      actualValue = condition => condition.note
    )

  def noNote(): AccessConditionMatcher[Option[String]] =
    createMatcher(
      propertyName = "note",
      expectedValue = None,
      actualValue = condition => condition.note
    )
}
