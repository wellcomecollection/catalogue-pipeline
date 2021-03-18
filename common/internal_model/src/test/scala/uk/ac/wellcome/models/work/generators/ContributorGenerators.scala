package uk.ac.wellcome.models.work.generators

import weco.catalogue.internal_model.work._

trait ContributorGenerators {
  def createPersonContributorWith(label: String,
                                  roles: List[ContributionRole] = List()) =
    Contributor(
      agent = Person(label = label),
      roles = roles
    )
}
