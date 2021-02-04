package uk.ac.wellcome.models.work.internal

case class DerivedWorkData(
  availableOnline: Boolean,
  contributorAgents: List[String] = Nil,
)

object DerivedWorkData {

  def none: DerivedWorkData =
    DerivedWorkData(
      availableOnline = false,
      contributorAgents = Nil
    )

  def apply(data: WorkData[_]): DerivedWorkData =
    DerivedWorkData(
      availableOnline = isAvailableOnline(data.items),
      contributorAgents = contributorAgents(data.contributors)
    )

  private def contributorAgents(
    contributors: List[Contributor[_]]): List[String] =
    contributors.map {
      case Contributor(_, agent, _) =>
        val ontologyType = agent.getClass.getSimpleName
        val label = agent.label
        s"$ontologyType:$label"
    }

  // The rule for determining if a Work is available online is:
  //
  //    It has at least one item with a digital location which might be available.
  //
  // We're deliberately a bit conservative about saying a Work isn't available
  // online.  If we have an item with a digital location, we only say that location
  // isn't available if:
  //
  //    - it has at least one access condition
  //    - every access condition has a non-empty AccessStatus, and the status is
  //      some flavour of unavailable
  //
  // See the test cases in DerivedDataTest for examples.
  //
  private def isAvailableOnline(items: List[Item[_]]): Boolean =
    items.exists { item =>
      item.locations
        .collect { case loc: DigitalLocationDeprecated => loc }
        .exists { locationIsAvailable }
    }

  private def locationIsAvailable(loc: DigitalLocationDeprecated): Boolean =
    loc.accessConditions.isEmpty || !allAccessConditionsAreUnavailable(loc)

  private def allAccessConditionsAreUnavailable(
    loc: DigitalLocationDeprecated): Boolean =
    loc.accessConditions
      .map { _.status }
      .forall {
        case Some(AccessStatus.Closed)      => true
        case Some(AccessStatus.Unavailable) => true
        case _                              => false
      }
}
