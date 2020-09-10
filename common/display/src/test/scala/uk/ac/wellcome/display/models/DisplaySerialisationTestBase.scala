package uk.ac.wellcome.display.models

import org.scalatest.Suite
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._

trait DisplaySerialisationTestBase {
  this: Suite =>

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

  def items(items: List[Item[Id.Minted]]) =
    items.map(item).mkString(",")

  def item(item: Item[Id.Minted]) =
    s"""
     {
       ${identifiers(item)}
       "type": "${item.ontologyType}",
       ${optionalString("title", item.title)}
       "locations": [${locations(item.locations)}]
     }
    """

  def locations(locations: List[LocationDeprecated]) =
    locations.map(location).mkString(",")

  def location(loc: LocationDeprecated) =
    loc match {
      case l: DigitalLocationDeprecated  => digitalLocation(l)
      case l: PhysicalLocationDeprecated => physicalLocation(l)
    }

  def digitalLocation(digitalLocation: DigitalLocationDeprecated) =
    s"""{
      "type": "${digitalLocation.ontologyType}",
      "locationType": ${locationType(digitalLocation.locationType)},
      "url": "${digitalLocation.url}"
      ${optionalObject("license", license, digitalLocation.license)},
      "accessConditions": ${accessConditions(digitalLocation.accessConditions)}
    }"""

  def physicalLocation(loc: PhysicalLocationDeprecated) =
    s"""
       {
        "type": "${loc.ontologyType}",
        "locationType": ${locationType(loc.locationType)},
        "label": "${loc.label}",
        "accessConditions": ${accessConditions(loc.accessConditions)}
       }
     """

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
  def identifiers(obj: HasId[Id.Minted]) =
    obj.id match {
      case Id.Identified(canonicalId, _, _) => s"""
        "id": "$canonicalId",
      """
      case Id.Unidentifiable                => ""
    }

  def abstractAgent(ag: AbstractAgent[Id.Minted]) =
    ag match {
      case a: Agent[Id.Minted]        => agent(a)
      case o: Organisation[Id.Minted] => organisation(o)
      case p: Person[Id.Minted]       => person(p)
      case m: Meeting[Id.Minted]      => meeting(m)
    }

  def person(person: Person[Id.Minted]) =
    s"""{
       ${identifiers(person)}
        "type": "Person",
        ${optionalString("prefix", person.prefix)}
        ${optionalString("numeration", person.numeration)}
        "label": "${person.label}"
      }"""

  def organisation(organisation: Organisation[Id.Minted]) =
    s"""{
       ${identifiers(organisation)}
        "type": "Organisation",
        "label": "${organisation.label}"
      }"""

  def meeting(meeting: Meeting[Id.Minted]) =
    s"""{
       ${identifiers(meeting)}
        "type": "Meeting",
        "label": "${meeting.label}"
      }"""

  def agent(agent: Agent[Id.Minted]) =
    s"""{
       ${identifiers(agent)}
        "type": "Agent",
        "label": "${agent.label}"
      }"""

  def period(period: Period[Id.Minted]) =
    s"""{
       ${identifiers(period)}
      "type": "Period",
      "label": "${period.label}"
    }"""

  def place(place: Place[Id.Minted]) =
    s"""{
       ${identifiers(place)}
      "type": "Place",
      "label": "${place.label}"
    }"""

  def concept(concept: Concept[Id.Minted]) =
    s"""{
       ${identifiers(concept)}
      "type": "Concept",
      "label": "${concept.label}"
    }"""

  def abstractRootConcept(abstractRootConcept: AbstractRootConcept[Id.Minted]) =
    abstractRootConcept match {
      case c: Concept[Id.Minted]      => concept(c)
      case p: Place[Id.Minted]        => place(p)
      case p: Period[Id.Minted]       => period(p)
      case a: Agent[Id.Minted]        => agent(a)
      case o: Organisation[Id.Minted] => organisation(o)
      case p: Person[Id.Minted]       => person(p)
      case m: Meeting[Id.Minted]      => meeting(m)
    }

  def concepts(concepts: List[AbstractRootConcept[Id.Minted]]) =
    concepts.map(abstractRootConcept).mkString(",")

  def subject(s: Subject[Id.Minted]): String =
    s"""
    {
      "label": "${s.label}",
      "type" : "${s.ontologyType}",
      "concepts": [ ${concepts(s.concepts)} ]
    }
    """

  def subjects(subjects: List[Subject[Id.Minted]]): String =
    subjects.map(subject).mkString(",")

  def genre(genre: Genre[Id.Minted]) =
    s"""
    {
      "label": "${genre.label}",
      "type" : "${genre.ontologyType}",
      "concepts": [ ${concepts(genre.concepts)} ]
    }
    """

  def genres(genres: List[Genre[Id.Minted]]) =
    genres.map(genre).mkString(",")

  def contributor(contributor: Contributor[Id.Minted]) =
    s"""
      {
        ${identifiers(contributor)}
        "agent": ${abstractAgent(contributor.agent)},
        "roles": ${toJson(contributor.roles).get},
        "type": "Contributor"
      }
    """.stripMargin

  def contributors(contributors: List[Contributor[Id.Minted]]) =
    contributors.map(contributor).mkString(",")

  def production(production: List[ProductionEvent[Id.Minted]]) =
    production.map(productionEvent).mkString(",")

  def workImageInclude(image: UnmergedImage[Id.Identified, Id.Minted]) =
    s"""
       {
         "id": "${image.id.canonicalId}",
         "type": "Image"
       }
    """.stripMargin

  def workImageIncludes(images: List[UnmergedImage[Id.Identified, Id.Minted]]) =
    images.map(workImageInclude).mkString(",")

  def productionEvent(event: ProductionEvent[Id.Minted]): String =
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

  def license(license: License) =
    s"""{
      "id": "${license.id}",
      "label": "${license.label}",
      "url": "${license.url}",
      "type": "${license.ontologyType}"
    }"""

  def identifier(identifier: SourceIdentifier) =
    s"""{
      "type": "Identifier",
      "identifierType": {
        "id": "${identifier.identifierType.id}",
        "label": "${identifier.identifierType.label}",
        "type": "${identifier.identifierType.ontologyType}"
      },
      "value": "${identifier.value}"
    }"""

  def locationType(locType: LocationType): String =
    s"""{
       |  "id": "${locType.id}",
       |  "label": "${locType.label}",
       |  "type": "LocationType"
       |}
     """.stripMargin

}
