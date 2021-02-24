package uk.ac.wellcome.models.work.internal

case class DerivedWorkData(
  availableOnline: Boolean,
  availabilities: List[Availability] = Nil,
  contributorAgents: List[String] = Nil,
)

object DerivedWorkData {

  def none: DerivedWorkData =
    DerivedWorkData(
      availableOnline = false,
      availabilities = Nil,
      contributorAgents = Nil
    )

  def apply(data: WorkData[_]): DerivedWorkData =
    DerivedWorkData(
      availableOnline =
        containsLocation(_.isInstanceOf[DigitalLocation])(data.items),
      availabilities = List(
        when(containsLocation(_.isInstanceOf[PhysicalLocation])(data.items))(
          Availability.InLibrary
        ),
        when(isAvailableOnline(data.items))(
          Availability.Online
        )
      ).flatten,
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

  private def isAvailableOnline: List[Item[_]] => Boolean =
    containsLocation {
      case location: DigitalLocation if location.isAvailable => true
      case _                                                 => false
    }

  private def containsLocation(predicate: Location => Boolean)(
    items: List[Item[_]]): Boolean =
    items.exists { item =>
      item.locations.exists(predicate)
    }

  private def when[T](condition: => Boolean)(property: T): Option[T] =
    if (condition) Some(property) else { None }
}
