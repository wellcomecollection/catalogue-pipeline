package uk.ac.wellcome.display.models

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

  def items(items: List[Item[Minted]]) =
    items.map(item).mkString(",")

  def item(item: Item[Minted]) =
    s"""
     {
       ${identifiers(item)}
       "type": "${item.ontologyType}",
       ${optionalString("title", item.title)}
       "locations": [${locations(item.locations)}]
     }
    """

  def locations(locations: List[Location]) =
    locations.map(location).mkString(",")

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
      ${optionalObject("license", license, digitalLocation.license)},
      "accessConditions": ${accessConditions(digitalLocation.accessConditions)}
    }"""

  def physicalLocation(loc: PhysicalLocation) =
    s"""
       {
        "type": "${loc.ontologyType}",
        "locationType": ${locationType(loc.locationType)},
        "label": "${loc.label}",
        "accessConditions": ${accessConditions(loc.accessConditions)}
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
        ${optionalString("to", cond.to, trailingComma = false)}
        ${optionalObject("status", accessStatus, cond.status)}
      }
    """

  def accessStatus(status: AccessStatus) = {
    s"""
      {
        "type": "AccessStatus",
        "id": "${DisplayAccessStatus(status).id}",
        "label": "${DisplayAccessStatus(status).label}"
      }
    """
  }
  def identifiers(obj: HasIdState[Minted]) =
    obj.id match {
      case Identified(canonicalId, _, _) => s"""
        "id": "$canonicalId",
      """
      case Unidentifiable                => ""
    }

  def abstractAgent(ag: AbstractAgent[Minted]) =
    ag match {
      case a: Agent[Minted]        => agent(a)
      case o: Organisation[Minted] => organisation(o)
      case p: Person[Minted]       => person(p)
      case m: Meeting[Minted]      => meeting(m)
    }

  def person(person: Person[Minted]) =
    s"""{
       ${identifiers(person)}
        "type": "Person",
        ${optionalString("prefix", person.prefix)}
        ${optionalString("numeration", person.numeration)}
        "label": "${person.label}"
      }"""

  def organisation(organisation: Organisation[Minted]) =
    s"""{
       ${identifiers(organisation)}
        "type": "Organisation",
        "label": "${organisation.label}"
      }"""

  def meeting(meeting: Meeting[Minted]) =
    s"""{
       ${identifiers(meeting)}
        "type": "Meeting",
        "label": "${meeting.label}"
      }"""

  def agent(agent: Agent[Minted]) =
    s"""{
       ${identifiers(agent)}
        "type": "Agent",
        "label": "${agent.label}"
      }"""

  def period(period: Period[Minted]) =
    s"""{
       ${identifiers(period)}
      "type": "Period",
      "label": "${period.label}"
    }"""

  def place(place: Place[Minted]) =
    s"""{
       ${identifiers(place)}
      "type": "Place",
      "label": "${place.label}"
    }"""

  def concept(concept: Concept[Minted]) =
    s"""{
       ${identifiers(concept)}
      "type": "Concept",
      "label": "${concept.label}"
    }"""

  def abstractRootConcept(abstractRootConcept: AbstractRootConcept[Minted]) =
    abstractRootConcept match {
      case c: Concept[Minted]      => concept(c)
      case p: Place[Minted]        => place(p)
      case p: Period[Minted]       => period(p)
      case a: Agent[Minted]        => agent(a)
      case o: Organisation[Minted] => organisation(o)
      case p: Person[Minted]       => person(p)
      case m: Meeting[Minted]      => meeting(m)
    }

  def concepts(concepts: List[AbstractRootConcept[Minted]]) =
    concepts.map(abstractRootConcept).mkString(",")

  def subject(s: Subject[Minted]): String =
    s"""
    {
      "label": "${s.label}",
      "type" : "${s.ontologyType}",
      "concepts": [ ${concepts(s.concepts)} ]
    }
    """

  def subjects(subjects: List[Subject[Minted]]): String =
    subjects.map(subject).mkString(",")

  def genre(genre: Genre[Minted]) =
    s"""
    {
      "label": "${genre.label}",
      "type" : "${genre.ontologyType}",
      "concepts": [ ${concepts(genre.concepts)} ]
    }
    """

  def genres(genres: List[Genre[Minted]]) =
    genres.map(genre).mkString(",")

  def contributor(contributor: Contributor[Minted]) =
    s"""
      {
        ${identifiers(contributor)}
        "agent": ${abstractAgent(contributor.agent)},
        "roles": ${toJson(contributor.roles).get},
        "type": "Contributor"
      }
    """.stripMargin

  def contributors(contributors: List[Contributor[Minted]]) =
    contributors.map(contributor).mkString(",")

  def production(production: List[ProductionEvent[Minted]]) =
    production.map(productionEvent).mkString(",")

  def productionEvent(event: ProductionEvent[Minted]): String =
    s"""
      {
        "label": "${event.label}",
        "dates": [${event.dates.map(period).mkString(",")}],
        "agents": [${event.agents.map(abstractAgent).mkString(",")}],
        "places": [${event.places.map(place).mkString(",")}],
        "type": "ProductionEvent"
      }
    """.stripMargin

  def workType(w: WorkType) =
    s"""
      {
        "id": "${w.id}",
        "label": "${w.label}",
        "type": "WorkType"
      }
    """.stripMargin
}
