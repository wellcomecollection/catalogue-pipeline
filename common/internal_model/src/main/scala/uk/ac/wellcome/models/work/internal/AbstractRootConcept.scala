package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.text.TextNormalisation._
import uk.ac.wellcome.models.parse.parsers.DateParser

import IdState._

sealed trait AbstractRootConcept[+Id] extends HasIdState[Id] {
  val label: String
}

sealed trait AbstractConcept[+Id] extends AbstractRootConcept[Id]

case class Concept[+Id](
  id: Id,
  label: String,
) extends AbstractConcept[Id]

object Concept {
  def apply[Id >: Unidentifiable.type](label: String): Concept[Id] =
    Concept(Unidentifiable, label)

  def normalised[Id](id: Id, label: String): Concept[Id] =
    Concept(id, trimTrailing(label, '.'))
}

case class Period[+Id](
  id: Id,
  label: String,
  range: Option[InstantRange],
) extends AbstractConcept[Id]

object Period {
  def apply[Id >: Unidentifiable.type](
    label: String,
    range: Option[InstantRange]): Period[Id] =
    Period(Unidentifiable, label, range)

  def apply[Id >: Unidentifiable.type](label: String): Period[Id] = {
    val normalisedLabel = trimTrailing(label, '.')
    Period(Unidentifiable, normalisedLabel, InstantRange.parse(normalisedLabel))
  }
}

case class Place[+Id](
  id: Id,
  label: String,
) extends AbstractConcept[Id]

object Place {
  def apply[Id >: Unidentifiable.type](label: String): Place[Id] =
    Place(Unidentifiable, label)

  def normalised[Id >: Unidentifiable.type](label: String): Place[Id] =
    Place(trimTrailing(label, ':'))
}

sealed trait AbstractAgent[+Id] extends AbstractRootConcept[Id]

case class Agent[+Id](
  id: Id,
  label: String,
) extends AbstractAgent[Id]

object Agent {
  def apply[Id >: Unidentifiable.type](label: String): Agent[Id] =
    Agent(Unidentifiable, label)

  def normalised[Id >: Unidentifiable.type](label: String): Agent[Id] = {
    Agent(trimTrailing(label, ','))
  }
}

case class Organisation[+Id](
  id: Id,
  label: String,
) extends AbstractAgent[Id]

object Organisation {
  def apply[Id >: Unidentifiable.type](label: String): Organisation[Id] =
    Organisation(Unidentifiable, label)

  def normalised[Id >: Unidentifiable.type](label: String): Organisation[Id] =
    Organisation(trimTrailing(label, ','))
}

case class Person[+Id](
  id: Id,
  label: String,
  prefix: Option[String] = None,
  numeration: Option[String] = None
) extends AbstractAgent[Id]

object Person {
  def apply[Id >: Unidentifiable.type](label: String): Person[Id] =
    Person(Unidentifiable, label)

  def normalised[Id >: Unidentifiable.type](
    label: String,
    prefix: Option[String] = None,
    numeration: Option[String] = None
  ): Person[Id] =
    Person(
      id = Unidentifiable,
      label = trimTrailing(label, ','),
      prefix = prefix,
      numeration = numeration)
}

case class Meeting[+Id](
  id: Id,
  label: String
) extends AbstractAgent[Id]

object Meeting {
  def apply[Id >: Unidentifiable.type](label: String): Meeting[Id] =
    Meeting(Unidentifiable, label)

  def normalised[Id >: Unidentifiable.type](label: String): Meeting[Id] =
    Meeting(trimTrailing(label, ','))
}
