package weco.catalogue.display_model.test.util

import io.circe.Json
import org.scalatest.Suite
import weco.catalogue.display_model.locations.{
  DisplayAccessMethod,
  DisplayAccessStatus,
  DisplayLocationType
}
import weco.catalogue.internal_model.identifiers.{
  HasId,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work._
import weco.json.JsonUtil._

import scala.util.{Failure, Success}

trait DisplaySerialisationTestBase {
  this: Suite =>

  implicit class JsonStringOps(s: String) {
    def tidy: String = {
      val tidiedFields =
        s
          // Replace anything that looks like '"key": None,' in the output.
          .replaceAll(""""[a-zA-Z]+": None,""".stripMargin, "")
          // Unwrap anything that looks like '"key": Some({…})' in the output
          .replaceAll("""Some\(\{(.*)\}\)""", "{$1}")
          // Unwrap anything that looks like '"key": Some("…")' in the output
          .replaceAll("""Some\("(.*)"\)""", "\"$1\"")

      fromJson[Json](tidiedFields) match {
        case Success(j) => j.noSpaces
        case Failure(_) =>
          throw new IllegalArgumentException(
            s"Unable to parse JSON:\n$tidiedFields"
          )
      }
    }

    def toJson: String =
      Json.fromString(s).noSpaces
  }

  def items(items: List[Item[IdState.Minted]]) =
    items.map(item).mkString(",")

  def item(item: Item[IdState.Minted]): String =
    s"""
     {
       ${identifiers(item)}
       "type": "Item",
       "title": ${item.title.map(_.toJson)},
       "locations": [${locations(item.locations)}]
     }
    """.tidy

  def locations(locations: List[Location]) =
    locations.map(location).mkString(",")

  def location(loc: Location) =
    loc match {
      case l: DigitalLocation  => digitalLocation(l)
      case l: PhysicalLocation => physicalLocation(l)
    }

  def digitalLocation(loc: DigitalLocation): String =
    s"""{
      "type": "DigitalLocation",
      "locationType": ${locationType(loc.locationType)},
      "url": "${loc.url}",
      "license": ${loc.license.map(license)},
      "credit": ${loc.credit.map(_.toJson)},
      "linkText": ${loc.linkText.map(_.toJson)},
      "accessConditions": ${accessConditions(loc.accessConditions)}
    }""".tidy

  def physicalLocation(loc: PhysicalLocation): String =
    s"""
       {
        "type": "PhysicalLocation",
        "locationType": ${locationType(loc.locationType)},
        "label": "${loc.label}",
        "license": ${loc.license.map(license)},
        "shelfmark": ${loc.shelfmark.map(_.toJson)},
        "accessConditions": ${accessConditions(loc.accessConditions)}
       }
     """.tidy

  def accessConditions(conds: List[AccessCondition]) =
    s"[${conds.map(accessCondition).mkString(",")}]"

  def accessCondition(cond: AccessCondition): String =
    s"""
      {
        "method": {
          "type": "AccessMethod",
          "id": "${DisplayAccessMethod(cond.method).id}",
          "label": "${DisplayAccessMethod(cond.method).label}"
        },
        "terms": ${cond.terms.map(_.toJson)},
        "status": ${cond.status.map(accessStatus)},
        "type": "AccessCondition"
      }
    """.tidy

  def accessStatus(status: AccessStatus): String =
    s"""{
       |  "type": "AccessStatus",
       |  "id": ${DisplayAccessStatus(status).id.toJson},
       |  "label": ${DisplayAccessStatus(status).label.toJson}
       |}
       |""".stripMargin.tidy

  def identifiers(obj: HasId[IdState.Minted]) =
    obj.id match {
      case IdState.Identified(canonicalId, _, _) => s"""
        "id": "$canonicalId",
      """
      case IdState.Unidentifiable => ""
    }

  def abstractAgent(ag: AbstractAgent[IdState.Minted]) =
    ag match {
      case a: Agent[IdState.Minted]        => agent(a)
      case o: Organisation[IdState.Minted] => organisation(o)
      case p: Person[IdState.Minted]       => person(p)
      case m: Meeting[IdState.Minted]      => meeting(m)
    }

  def person(person: Person[IdState.Minted]): String =
    s"""
       |{
       |  ${identifiers(person)}
       |  "type": "Person",
       |  "prefix": ${person.prefix.map(_.toJson)},
       |  "numeration": ${person.numeration.map(_.toJson)},
       |  "label": "${person.label}"
       |}
       |""".stripMargin.tidy

  def organisation(organisation: Organisation[IdState.Minted]) =
    s"""{
       ${identifiers(organisation)}
        "type": "Organisation",
        "label": "${organisation.label}"
      }"""

  def meeting(meeting: Meeting[IdState.Minted]) =
    s"""{
       ${identifiers(meeting)}
        "type": "Meeting",
        "label": "${meeting.label}"
      }"""

  def agent(agent: Agent[IdState.Minted]) =
    s"""{
       ${identifiers(agent)}
        "type": "Agent",
        "label": "${agent.label}"
      }"""

  def period(period: Period[IdState.Minted]) =
    s"""{
       ${identifiers(period)}
      "type": "Period",
      "label": "${period.label}"
    }"""

  def place(place: Place[IdState.Minted]) =
    s"""{
       ${identifiers(place)}
      "type": "Place",
      "label": "${place.label}"
    }"""

  def concept(concept: Concept[IdState.Minted]) =
    s"""{
       ${identifiers(concept)}
      "type": "Concept",
      "label": "${concept.label}"
    }"""
  def genreConcept(genre: GenreConcept[IdState.Minted]) =
    s"""{
     ${identifiers(genre)}
    "type": "Genre",
    "label": "${genre.label}"
  }"""

  def abstractRootConcept(
    abstractRootConcept: AbstractRootConcept[IdState.Minted]
  ) =
    abstractRootConcept match {
      case c: Concept[IdState.Minted]      => concept(c)
      case g: GenreConcept[IdState.Minted] => genreConcept(g)
      case p: Place[IdState.Minted]        => place(p)
      case p: Period[IdState.Minted]       => period(p)
      case a: Agent[IdState.Minted]        => agent(a)
      case o: Organisation[IdState.Minted] => organisation(o)
      case p: Person[IdState.Minted]       => person(p)
      case m: Meeting[IdState.Minted]      => meeting(m)
    }

  def concepts(concepts: List[AbstractRootConcept[IdState.Minted]]) =
    concepts.map(abstractRootConcept).mkString(",")

  def subject(
    s: Subject[IdState.Minted],
    showConcepts: Boolean = true
  ): String =
    s"""
    {
      "label": "${s.label}",
      "type" : "Subject",
      "concepts": [ ${if (showConcepts) concepts(s.concepts) else ""} ]
    }
    """

  def subjects(subjects: List[Subject[IdState.Minted]]): String =
    subjects.map(subject(_)).mkString(",")

  def genre(genre: Genre[IdState.Minted]) =
    s"""
    {
      "label": "${genre.label}",
      "type" : "Genre",
      "concepts": [ ${concepts(genre.concepts)} ]
    }
    """

  def genres(genres: List[Genre[IdState.Minted]]) =
    genres.map(genre).mkString(",")

  def contributor(contributor: Contributor[IdState.Minted]) =
    s"""
      {
        ${identifiers(contributor)}
        "agent": ${abstractRootConcept(contributor.agent)},
        "roles": ${toJson(contributor.roles).get},
        "type": "Contributor"
      }
    """

  def contributors(contributors: List[Contributor[IdState.Minted]]) =
    contributors.map(contributor).mkString(",")

  def production(production: List[ProductionEvent[IdState.Minted]]) =
    production.map(productionEvent).mkString(",")

  def availabilities(availabilities: Set[Availability]) =
    availabilities.map(availability).mkString(",")

  def languages(ls: List[Language]): String =
    ls.map(language).mkString(",")

  def workImageInclude(image: ImageData[IdState.Identified]) =
    s"""
       |{
       |  "id": "${image.id.canonicalId}",
       |  "type": "Image"
       |}
       |""".stripMargin

  def workImageIncludes(images: List[ImageData[IdState.Identified]]) =
    images.map(workImageInclude).mkString(",")

  def availability(availability: Availability): String =
    s"""
       |{
       |  "id": "${availability.id}",
       |  "label": "${availability.label}",
       |  "type": "Availability"
       |}
       |""".stripMargin

  def productionEvent(event: ProductionEvent[IdState.Minted]): String =
    s"""
       |{
       |  "label": "${event.label}",
       |  "dates": [${event.dates.map(period).mkString(",")}],
       |  "agents": [${event.agents.map(abstractAgent).mkString(",")}],
       |  "places": [${event.places.map(place).mkString(",")}],
       |  "type": "ProductionEvent"
       |}
       |""".stripMargin.tidy

  def format(fmt: Format): String =
    s"""
       |{
       |  "id": "${fmt.id}",
       |  "label": "${fmt.label}",
       |  "type": "Format"
       |}
       |""".stripMargin.tidy

  def language(lang: Language): String =
    s"""
       {
         "id": "${lang.id}",
         "label": "${lang.label}",
         "type": "Language"
       }
     """

  def license(license: License): String =
    s"""{
      "id": "${license.id}",
      "label": "${license.label}",
      "url": "${license.url}",
      "type": "License"
    }""".tidy

  def identifier(identifier: SourceIdentifier) =
    s"""{
      "type": "Identifier",
      "identifierType": {
        "id": "${identifier.identifierType.id}",
        "label": "${identifier.identifierType.label}",
        "type": "IdentifierType"
      },
      "value": "${identifier.value}"
    }"""

  def locationType(locType: LocationType): String =
    s"""{
         "id": "${DisplayLocationType(locType).id}",
         "label": "${DisplayLocationType(locType).label}",
         "type": "LocationType"
       }
     """ stripMargin

  def singleHoldings(h: Holdings): String = {
    val enumerations = h.enumeration.map(_.toJson)

    s"""
       |{
       |  "note": ${h.note.map(_.toJson)},
       |  "enumeration": [${enumerations.mkString(",")}],
       |  "location": ${h.location.map(location)},
       |  "type": "Holdings"
       |}
       |""".stripMargin.tidy
  }

  def listOfHoldings(hs: List[Holdings]): String =
    hs.map {
      singleHoldings
    }.mkString(",")
}
