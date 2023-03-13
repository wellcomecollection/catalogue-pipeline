package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.{HasId, IdState}

sealed trait AbstractRootConcept[+State] extends HasId[State] {
  val label: String
}

object AbstractRootConcept {

  private val conceptTypeMap
    : Map[String, (IdState.Identifiable, String) => AbstractRootConcept[
      IdState.Identifiable
    ]] = Map(
    "Concept" -> (new Concept[IdState.Identifiable](_, _)),
    "Agent" -> (new Agent[IdState.Identifiable](_, _)),
    "Place" -> (new Place[IdState.Identifiable](_, _)),
    "Period" -> (new Period[IdState.Identifiable](_, _)),
    "Meeting" -> (new Meeting[IdState.Identifiable](_, _)),
    "Organisation" -> (new Organisation[IdState.Identifiable](_, _)),
    "Person" -> (new Person[IdState.Identifiable](_, _))
  )

  def apply[State >: IdState.Identifiable](
    ontologyType: String,
    id: IdState.Identifiable,
    label: String
  ): AbstractRootConcept[IdState.Identifiable] =
    conceptTypeMap(ontologyType)(id, label)

}

sealed trait AbstractConcept[+State] extends AbstractRootConcept[State]

case class Concept[+State](
  id: State,
  label: String
) extends AbstractConcept[State]

object Concept {
  def apply(label: String): Concept[IdState.Unidentifiable.type] =
    Concept(id = IdState.Unidentifiable, label = label)
}
case class GenreConcept[+State](
  id: State,
  label: String
) extends AbstractConcept[State]

object GenreConcept {
  def apply(label: String): GenreConcept[IdState.Unidentifiable.type] =
    GenreConcept(id = IdState.Unidentifiable, label = label)
}

case class Period[+State](
  id: State,
  label: String,
  range: Option[InstantRange] = None
) extends AbstractConcept[State]

object Period {
  def apply(
    label: String,
    range: InstantRange
  ): Period[IdState.Unidentifiable.type] =
    Period(
      id = IdState.Unidentifiable,
      label = label,
      range = Some(range)
    )
}

case class Place[+State](
  id: State,
  label: String
) extends AbstractConcept[State]

object Place {
  def apply(label: String): Place[IdState.Unidentifiable.type] =
    Place(id = IdState.Unidentifiable, label = label)
}

sealed trait AbstractAgent[+State] extends AbstractRootConcept[State]

case class Agent[+State](
  id: State,
  label: String
) extends AbstractAgent[State]

object Agent {
  def apply(label: String): Agent[IdState.Unidentifiable.type] =
    Agent(id = IdState.Unidentifiable, label = label)
}

case class Organisation[+State](
  id: State,
  label: String
) extends AbstractAgent[State]

object Organisation {
  def apply(label: String): Organisation[IdState.Unidentifiable.type] =
    Organisation(id = IdState.Unidentifiable, label = label)
}

case class Person[+State](
  id: State,
  label: String,
  prefix: Option[String] = None,
  numeration: Option[String] = None
) extends AbstractAgent[State]

object Person {
  def apply(label: String): Person[IdState.Unidentifiable.type] =
    Person(id = IdState.Unidentifiable, label = label)
}

case class Meeting[+State](
  id: State,
  label: String
) extends AbstractAgent[State]

object Meeting {
  def apply(label: String): Meeting[IdState.Unidentifiable.type] =
    Meeting(id = IdState.Unidentifiable, label = label)
}
