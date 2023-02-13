package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.work.{
  ContributionRole,
  Contributor,
  Person
}

trait ContributorGenerators {
  def createPersonContributorWith(
    label: String,
    roles: List[ContributionRole] = List()
  ) =
    Contributor(
      agent = Person(label = label),
      roles = roles
    )
}
