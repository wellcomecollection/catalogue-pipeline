package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.text.TextNormalisation._
import uk.ac.wellcome.models.parse.parsers.DateParser

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
    Concept(id, label.trimTrailing('.'))
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
    val normalisedLabel = label.trimTrailing('.')
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
    label: String): Agent[State] = {
    Agent(label.trimTrailing(','))
  }
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
  def apply[State >: IdState.Unidentifiable.type](
    label: String): Person[State] =
    Person(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String,
    prefix: Option[String] = None,
    numeration: Option[String] = None
  ): Person[State] =
    Person(
      id = IdState.Unidentifiable,
      label = label.trimTrailing(','),
      prefix = prefix,
      numeration = numeration)
}

case class Meeting[+State](
  id: State,
  label: String
) extends AbstractAgent[State]

object Meeting {
  def apply[State >: IdState.Unidentifiable.type](
    label: String): Meeting[State] =
    Meeting(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String): Meeting[State] =
    Meeting(label.trimTrailing(','))
}
