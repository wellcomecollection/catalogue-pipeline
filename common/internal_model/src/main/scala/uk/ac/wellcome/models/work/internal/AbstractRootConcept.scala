package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.text.TextNormalisation._
import uk.ac.wellcome.models.parse.parsers.DateParser

sealed trait AbstractRootConcept[+DataId] extends HasId[DataId] {
  val label: String
}

sealed trait AbstractConcept[+DataId] extends AbstractRootConcept[DataId]

case class Concept[+DataId](
  id: DataId,
  label: String,
) extends AbstractConcept[DataId]

object Concept {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Concept[DataId] =
    Concept(Id.Unidentifiable, label)

  def normalised[DataId](id: DataId, label: String): Concept[DataId] =
    Concept(id, trimTrailing(label, '.'))
}

case class Period[+DataId](
  id: DataId,
  label: String,
  range: Option[InstantRange],
) extends AbstractConcept[DataId]

object Period {
  def apply[DataId >: Id.Unidentifiable.type](
    label: String,
    range: Option[InstantRange]): Period[DataId] =
    Period(Id.Unidentifiable, label, range)

  def apply[DataId >: Id.Unidentifiable.type](label: String): Period[DataId] = {
    val normalisedLabel = trimTrailing(label, '.')
    Period(Id.Unidentifiable, normalisedLabel, InstantRange.parse(normalisedLabel))
  }
}

case class Place[+DataId](
  id: DataId,
  label: String,
) extends AbstractConcept[DataId]

object Place {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Place[DataId] =
    Place(Id.Unidentifiable, label)

  def normalised[DataId >: Id.Unidentifiable.type](label: String): Place[DataId] =
    Place(trimTrailing(label, ':'))
}

sealed trait AbstractAgent[+DataId] extends AbstractRootConcept[DataId]

case class Agent[+DataId](
  id: DataId,
  label: String,
) extends AbstractAgent[DataId]

object Agent {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Agent[DataId] =
    Agent(Id.Unidentifiable, label)

  def normalised[DataId >: Id.Unidentifiable.type](label: String): Agent[DataId] = {
    Agent(trimTrailing(label, ','))
  }
}

case class Organisation[+DataId](
  id: DataId,
  label: String,
) extends AbstractAgent[DataId]

object Organisation {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Organisation[DataId] =
    Organisation(Id.Unidentifiable, label)

  def normalised[DataId >: Id.Unidentifiable.type](label: String): Organisation[DataId] =
    Organisation(trimTrailing(label, ','))
}

case class Person[+DataId](
  id: DataId,
  label: String,
  prefix: Option[String] = None,
  numeration: Option[String] = None
) extends AbstractAgent[DataId]

object Person {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Person[DataId] =
    Person(Id.Unidentifiable, label)

  def normalised[DataId >: Id.Unidentifiable.type](
    label: String,
    prefix: Option[String] = None,
    numeration: Option[String] = None
  ): Person[DataId] =
    Person(
      id = Id.Unidentifiable,
      label = trimTrailing(label, ','),
      prefix = prefix,
      numeration = numeration)
}

case class Meeting[+DataId](
  id: DataId,
  label: String
) extends AbstractAgent[DataId]

object Meeting {
  def apply[DataId >: Id.Unidentifiable.type](label: String): Meeting[DataId] =
    Meeting(Id.Unidentifiable, label)

  def normalised[DataId >: Id.Unidentifiable.type](label: String): Meeting[DataId] =
    Meeting(trimTrailing(label, ','))
}
