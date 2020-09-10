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

  def normalised[State](id: State, label: String): Concept[State] =
    Concept(id, trimTrailing(label, '.'))
}

case class Period[+State](
  id: State,
  label: String,
  range: Option[InstantRange],
) extends AbstractConcept[State]

object Period {
  def apply[State >: IdState.Unidentifiable.type](
    label: String,
    range: Option[InstantRange]): Period[State] =
    Period(IdState.Unidentifiable, label, range)

  def apply[State >: IdState.Unidentifiable.type](
    label: String): Period[State] = {
    val normalisedLabel = trimTrailing(label, '.')
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
    Place(trimTrailing(label, ':'))
}

sealed trait AbstractAgent[+State] extends AbstractRootConcept[State]

case class Agent[+State](
  id: State,
  label: String,
) extends AbstractAgent[State]

object Agent {
  def apply[State >: IdState.Unidentifiable.type](label: String): Agent[State] =
    Agent(IdState.Unidentifiable, label)

  def normalised[State >: IdState.Unidentifiable.type](
    label: String): Agent[State] = {
    Agent(trimTrailing(label, ','))
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
    Organisation(trimTrailing(label, ','))
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
      label = trimTrailing(label, ','),
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
    Meeting(trimTrailing(label, ','))
}
