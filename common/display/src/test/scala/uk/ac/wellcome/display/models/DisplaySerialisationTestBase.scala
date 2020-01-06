package uk.ac.wellcome.display.models

import io.circe.Json
import io.circe.parser._
import org.scalatest.Suite
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._

trait DisplaySerialisationTestBase { this: Suite =>

  def optionalString(fieldName: String,
                     maybeStringValue: Option[String],
                     trailingComma: Boolean = true): String =
    maybeStringValue match {
      case None => ""
      case Some(value) =>
        s"""
          "$fieldName": "$value"
          ${if (trailingComma) "," else ""}
        """
    }

  def optionalObject[T](fieldName: String,
                        formatter: T => String,
                        maybeObjectValue: Option[T],
                        firstField: Boolean = false) =
    maybeObjectValue match {
      case None => ""
      case Some(o) =>
        s"""
           ${if (!firstField) ","}"$fieldName": ${formatter(o)}
         """
    }

  def items(identifiedItems: List[Displayable[Item]]) =
    identifiedItems
      .map {
        case it: Identified[Item] =>
          identifiedItem(it)
        case it: Unidentifiable[Item] =>
          unidentifiableItem(it)
      }
      .mkString(",")

  def unidentifiableItem(it: Unidentifiable[Item]) =
    s"""
      {
        "type": "${it.agent.ontologyType}",
        ${itemTitle(it.agent)}
        "locations": [${locations(it.agent.locations)}]
      }
    """

  def identifiedItem(it: Identified[Item]) =
    s"""
      {
        "id": "${it.canonicalId}",
        "type": "${it.agent.ontologyType}",
        ${itemTitle(it.agent)}
        "locations": [${locations(it.agent.locations)}]
      }
    """

  def itemTitle(item: Item) =
    item.title match {
      case Some(title) => s""""title": "$title","""
      case None        => ""
    }

  def locations(locations: List[Location]) =
    locations
      .map {
        location(_)
      }
      .mkString(",")

  def location(loc: Location) =
    loc match {
      case l: DigitalLocation  => digitalLocation(l)
      case l: PhysicalLocation => physicalLocation(l)
    }

  def digitalLocation(digitalLocation: DigitalLocation) =
    s"""{
      "type": "${digitalLocation.ontologyType}",
      "locationType": ${locationType(digitalLocation.locationType)},
      "url": "${digitalLocation.url}"
      ${optionalObject("license", license, digitalLocation.license)}
      ${optionalObject(
      "accessConditions",
      accessConditions,
      digitalLocation.accessConditions)}
    }"""

  def physicalLocation(loc: PhysicalLocation) =
    s"""
       {
        "type": "${loc.ontologyType}",
        "locationType": ${locationType(loc.locationType)},
        "label": "${loc.label}"
        ${optionalObject(
        "accessConditions",
        accessConditions,
        loc.accessConditions)}
       }
     """

  def locationType(locType: LocationType): String

  def license(license: License): String

  def accessConditions(conds: List[AccessCondition]) =
    s"[${conds.map(accessCondition).mkString(",")}]"

  def accessCondition(cond: AccessCondition) =
    s"""
      {
        "type": "AccessCondition",
        ${optionalString("terms", cond.terms)}
        ${optionalString("to", cond.to.map(_.toString))}
        ${accessStatus(cond.status)}
      }
    """

  def accessStatus(status: AccessStatus) = {
    s"""
      "status": {
        "type": "AccessStatus",
        "id": "${DisplayAccessStatus(status).id}",
        "label": "${DisplayAccessStatus(status).label}"
      }
    """
  }

  def identifier(identifier: SourceIdentifier): String

  // Some of our fields can be optionally identified (e.g. creators).
  //
  // Values in these fields are wrapped in either "Unidentifiable" or
  // "Identified".  In the first case, we use the default serialisation
  // unmodified.  In the second case, we modify the JSON to include
  // the "id" field and the "identifiers" field.
  //
  def identifiedOrUnidentifiable[T](displayable: Displayable[T],
                                    serialise: T => String) =
    displayable match {
      case ag: Unidentifiable[T] => serialise(ag.agent)
      case disp: Identified[T] =>
        val agent = parse(serialise(disp.agent)).right.get.asObject.get
        val identifiersJson = disp.identifiers.map { sourceIdentifier =>
          parse(identifier(sourceIdentifier)).right.get
        }
        val newJson = ("id", Json.fromString(disp.canonicalId)) +: (
          "identifiers",
          Json.arr(identifiersJson: _*)) +: agent
        Json.fromJsonObject(newJson).spaces2
    }

  def abstractAgent(ag: AbstractAgent) =
    ag match {
      case a: Agent        => agent(a)
      case o: Organisation => organisation(o)
      case p: Person       => person(p)
      case m: Meeting      => meeting(m)
    }

  def person(p: Person) = {
    s"""{
        "type": "Person",
        ${optionalString("prefix", p.prefix)}
        ${optionalString("numeration", p.numeration)}
        "label": "${p.label}"
      }"""
  }

  def organisation(o: Organisation) = {
    s"""{
        "type": "Organisation",
        "label": "${o.label}"
      }"""
  }

  def meeting(m: Meeting) = {
    s"""{
        "type": "Meeting",
        "label": "${m.label}"
      }"""
  }

  def agent(a: Agent) = {
    s"""{
        "type": "Agent",
        "label": "${a.label}"
      }"""
  }

  def period(p: Period) =
    s"""{
      "type": "Period",
      "label": "${p.label}"
    }"""

  def place(p: Place) =
    s"""{
      "type": "Place",
      "label": "${p.label}"
    }"""

  def ontologyType(concept: AbstractRootConcept) =
    concept match {
      case _: Concept      => "Concept"
      case _: Place        => "Place"
      case _: Period       => "Period"
      case _: Agent        => "Agent"
      case _: Organisation => "Organisation"
      case _: Person       => "Person"
      case _: Meeting      => "Meeting"
    }

  def concept(concept: AbstractRootConcept) = {
    s"""
    {
      "type": "${ontologyType(concept)}",
      "label": "${concept.label}"
    }
    """
  }

  def concepts(concepts: List[Displayable[AbstractRootConcept]]) =
    concepts
      .map { c =>
        identifiedOrUnidentifiable(c, concept)
      }
      .mkString(",")

  private def subject(s: Subject[Displayable[AbstractRootConcept]]): String =
    s"""
    {
      "label": "${s.label}",
      "type" : "${s.ontologyType}",
      "concepts": [ ${concepts(s.concepts)} ]
    }
    """

  def subjects(
    subjects: List[Displayable[Subject[Displayable[AbstractRootConcept]]]])
    : String =
    subjects
      .map { s =>
        identifiedOrUnidentifiable(s, subject)
      }
      .mkString(",")

  def genre(g: Genre[Displayable[AbstractConcept]]) =
    s"""
    {
      "label": "${g.label}",
      "type" : "${g.ontologyType}",
      "concepts": [ ${concepts(g.concepts)} ]
    }
    """

  def genres(genres: List[Genre[Displayable[AbstractConcept]]]) =
    genres
      .map { genre(_) }
      .mkString(",")

  def contributor(contributors: Contributor[Displayable[AbstractAgent]]) =
    s"""
       |{
       |  "agent": ${identifiedOrUnidentifiable(
         contributors.agent,
         abstractAgent)},
       |  "roles": ${toJson(contributors.roles).get},
       |  "type": "Contributor"
       |}
     """.stripMargin

  def contributors(c: List[Contributor[Displayable[AbstractAgent]]]) =
    c.map(contributor).mkString(",")

  def production(
    production: List[ProductionEvent[Displayable[AbstractAgent]]]) =
    production.map(productionEvent).mkString(",")

  def productionEvent(
    event: ProductionEvent[Displayable[AbstractAgent]]): String =
    s"""
       |{
       |  "label": "${event.label}",
       |  "dates": [${event.dates.map(period).mkString(",")}],
       |  "agents": [${event.agents
         .map(identifiedOrUnidentifiable(_, abstractAgent))
         .mkString(",")}],
       |  "places": [${event.places.map(place).mkString(",")}],
       |  "type": "ProductionEvent"
       |}
     """.stripMargin

  def workType(w: WorkType) =
    s"""
       |{
       |  "id": "${w.id}",
       |  "label": "${w.label}",
       |  "type": "WorkType"
       |}
     """.stripMargin
}
