package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.work.Contributor

trait ContributorMatchers {

  class HaveRoles(expectedRoles: List[String])
      extends HavePropertyMatcher[Contributor[Any], List[String]] {
    override def apply(
      contributor: Contributor[Any]): HavePropertyMatchResult[List[String]] = {
      val actualRoles = contributor.roles.map(_.label)
      HavePropertyMatchResult[List[String]](
        matches = actualRoles == expectedRoles,
        propertyName = "roles",
        expectedValue = expectedRoles,
        actualValue = actualRoles
      )
    }
  }

  /**
    * Match a Contributor's roles against a list of role names
    */
  def roles(expectedRoles: List[String])
    : HavePropertyMatcher[Contributor[Any], List[String]] =
    new HaveRoles(expectedRoles)

}

object ContributorMatchers extends ContributorMatchers
