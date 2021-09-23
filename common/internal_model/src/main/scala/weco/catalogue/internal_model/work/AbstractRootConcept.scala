package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.parse.parsers.DateParser
import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.identifiers.{HasId, IdState}

sealed trait AbstractRootConcept[+State] extends HasId[State] {
  val label: String
}

sealed trait AbstractConcept[+State] extends AbstractRootConcept[State]

case class Concept[+State](
  id: State,
  label: String,
) extends AbstractConcept[State]

object Concept {
  def apply(label: String): Concept[IdState.Unidentifiable.type] =
    Concept(id = IdState.Unidentifiable, label = label)
}

case class Period[+State](
  id: State,
  label: String,
  range: Option[InstantRange] = None,
) extends AbstractConcept[State]

object Period {
  def apply[State >: IdState.Unidentifiable.type](
    label: String,
    range: Option[InstantRange]): Period[State] =
    Period(IdState.Unidentifiable, label, range)

  def apply[State >: IdState.Unidentifiable.type](
    label: String): Period[State] = {
    val normalisedLabel = label.trimTrailingPeriod
    Period(
      IdState.Unidentifiable,
      normalisedLabel,
      InstantRange.parse(normalisedLabel))
  }
}

case class Place[+State](
  id: State,
  label: String,
) extends AbstractConcept[State]

object Place {
  def apply(label: String): Place[IdState.Unidentifiable.type] =
    Place(id = IdState.Unidentifiable, label = label)
}

sealed trait AbstractAgent[+State] extends AbstractRootConcept[State] {
  def typedLabel: String = s"${this.getClass.getSimpleName}:$label"
}

case class Agent[+State](
  id: State,
  label: String,
) extends AbstractAgent[State]

object Agent {
  def apply(label: String): Agent[IdState.Unidentifiable.type] =
    Agent(id = IdState.Unidentifiable, label = label)
}

case class Organisation[+State](
  id: State,
  label: String,
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
