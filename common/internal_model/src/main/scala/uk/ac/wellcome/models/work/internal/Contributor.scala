package uk.ac.wellcome.models.work.internal

case class Contributor[+Id](
  id: Id,
  agent: AbstractAgent[Id],
  roles: List[ContributionRole] = Nil,
  ontologyType: String = "Contributor"
) extends HasIdState[Id]

object Contributor {

  def apply[Id >: Unidentifiable.type](
    agent: AbstractAgent[Id],
    roles: List[ContributionRole]): Contributor[Id] =
    Contributor(Unidentifiable, agent, roles)
}
