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
  def apply[State >: IdState.Unidentifiable.type](
    label: String): Concept[State] =
    Concept(IdState.Unidentifiable, label)

  def normalised[State](id: State = IdState.Unidentifiable,
                        label: String): Concept[State] =
    Concept(id, label.trimTrailingPeriod)
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
  def apply[State >: IdState.Unidentifiable.type](label: String): Place[State] =
    Place(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String): Place[State] =
    Place(label.trimTrailing(':'))
}

sealed trait AbstractAgent[+State] extends AbstractRootConcept[State] {
  def typedLabel: String = s"${this.getClass.getSimpleName}:$label"
}

case class Agent[+State](
  id: State,
  label: String,
) extends AbstractAgent[State]

object Agent {
  def apply[State >: IdState.Unidentifiable.type](label: String): Agent[State] =
    Agent(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String): Agent[State] =
    Agent(label.trimTrailing(','))
}

case class Organisation[+State](
  id: State,
  label: String,
) extends AbstractAgent[State]

object Organisation {
  def apply[State >: IdState.Unidentifiable.type](
    label: String): Organisation[State] =
    Organisation(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String): Organisation[State] =
    Organisation(label.trimTrailing(','))
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
