package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.{HasId, IdState}

case class Contributor[+State](
  id: State,
  agent: AbstractAgent[State],
  roles: List[ContributionRole] = Nil,
  // This indicates whether a contributor is suitable for including in
  // the list of primary contributors that we display in search results
  // and at the top of the work page.
  //
  // For now, we assume this is true to keep the change small and
  // self-contained; there are only a few places where we mark a
  // contributor as not-primary.
  primary: Boolean = true
) extends HasId[State]

object Contributor {

  def apply[State >: IdState.Unidentifiable.type](
    agent: AbstractAgent[State],
    roles: List[ContributionRole],
  ): Contributor[State] =
    Contributor(id = IdState.Unidentifiable, agent, roles)

  def apply[State >: IdState.Unidentifiable.type](
    agent: AbstractAgent[State],
    roles: List[ContributionRole],
    primary: Boolean
  ): Contributor[State] =
    Contributor(id = IdState.Unidentifiable, agent, roles, primary)
}
