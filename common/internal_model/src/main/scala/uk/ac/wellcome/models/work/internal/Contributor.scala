package uk.ac.wellcome.models.work.internal

case class Contributor[+T <: IdentityState[AbstractAgent]](
  agent: T,
  roles: List[ContributionRole] = List(),
  date: Option[Period] = None,
  ontologyType: String = "Contributor"
)
