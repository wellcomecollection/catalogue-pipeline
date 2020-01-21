package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.text.TextNormalisation._
import uk.ac.wellcome.models.parse.parsers.DateParser

sealed trait AbstractRootConcept {
  val label: String
}

sealed trait AbstractConcept extends AbstractRootConcept

case class Concept(label: String) extends AbstractConcept
object Concept {
  def normalised(label: String): Concept = {
    Concept(trimTrailing(label, '.'))
  }
}

case class Period(label: String, range: Option[InstantRange])
    extends AbstractConcept
object Period {
  def apply(label: String): Period = {
    val normalisedLabel = trimTrailing(label, '.')
    Period(normalisedLabel, InstantRange.parse(normalisedLabel))
  }
}

case class Place(label: String) extends AbstractConcept
object Place {
  def normalised(label: String): Place = {
    Place(trimTrailing(label, ':'))
  }
}
sealed trait AbstractAgent extends AbstractRootConcept

case class Agent(
  label: String
) extends AbstractAgent
object Agent {
  def normalised(label: String): Agent = {
    Agent(trimTrailing(label, ','))
  }
}

case class Organisation(
  label: String
) extends AbstractAgent
object Organisation {
  def normalised(label: String): Organisation = {
    Organisation(trimTrailing(label, ','))
  }
}

case class Person(label: String,
                  prefix: Option[String] = None,
                  numeration: Option[String] = None)
    extends AbstractAgent
object Person {
  def normalised(label: String,
                 prefix: Option[String] = None,
                 numeration: Option[String] = None): Person = {
    Person(
      label = trimTrailing(label, ','),
      prefix = prefix,
      numeration = numeration)
  }
}

case class Meeting(label: String) extends AbstractAgent
object Meeting {
  def normalised(label: String): Meeting =
    Meeting(trimTrailing(label, ','))
}
