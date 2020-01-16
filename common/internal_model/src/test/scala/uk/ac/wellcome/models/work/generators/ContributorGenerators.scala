package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait ContributorGenerators {
  def createPersonContributorWith(label: String,
                                  roles: List[ContributionRole] = List()) =
    Contributor(
      agent = Person(label = label),
      roles = roles
    )
}
