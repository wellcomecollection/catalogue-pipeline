package uk.ac.wellcome.models.work.internal

case class Contributor[+State](
  id: State,
  agent: AbstractAgent[State],
  roles: List[ContributionRole] = Nil,
  ontologyType: String = "Contributor"
) extends HasId[State]

object Contributor {

  def apply[State >: IdState.Unidentifiable.type](
    agent: AbstractAgent[State],
    roles: List[ContributionRole]): Contributor[State] =
    Contributor(IdState.Unidentifiable, agent, roles)
}
