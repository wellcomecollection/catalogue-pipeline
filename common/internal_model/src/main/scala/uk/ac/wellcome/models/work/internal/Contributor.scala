package uk.ac.wellcome.models.work.internal

case class Contributor[+DataId](
  id: DataId,
  agent: AbstractAgent[DataId],
  roles: List[ContributionRole] = Nil,
  ontologyType: String = "Contributor"
) extends HasId[DataId]

object Contributor {

  def apply[DataId >: Id.Unidentifiable.type](
    agent: AbstractAgent[DataId],
    roles: List[ContributionRole]): Contributor[DataId] =
    Contributor(Id.Unidentifiable, agent, roles)
}
