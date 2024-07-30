package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  Agent,
  Concept,
  Period,
  Place,
  ProductionEvent
}
import weco.pipeline.transformer.marc_common.exceptions.CataloguingException
import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcFieldOps,
  MarcRecord,
  MarcSubfield
}
import weco.pipeline.transformer.marc_common.transformers.parsers.MarcProductionEventParser
import weco.pipeline.transformer.transformers.{
  ConceptsTransformer,
  ParsedPeriod
}

object MarcProduction
    extends MarcDataTransformer
    with MarcFieldOps
    with ConceptsTransformer
    with Logging {
  type Output = List[ProductionEvent[IdState.Unminted]]

  def apply(record: MarcRecord, prefer264Field: Boolean = false): List[ProductionEvent[IdState.Unminted]] = {
    val productionEvents = (
      getProductionFrom260Fields(record),
      getProductionFrom264Fields(record),
      prefer264Field
    ) match {
      case (Nil, Nil, _)     => Nil
      case (from260, Nil, _) => from260
      case (Nil, from264, _) => from264
      case (_, from264, true) => from264
      case (from260, _, false) => from260
    }

    val marc008productionEvents = getProductionFrom008(record)

    (productionEvents, marc008productionEvents) match {
      // Use the dates from the first 008 production event if we couldn't parse those in 260/4
      case (
            firstEvent :: otherEvents,
            ProductionEvent(_, _, _, marc008dates, _) :: _
          )
          if firstEvent.dates.forall(
            _.range.isEmpty
          ) && marc008dates.nonEmpty =>
        // There is only ever 1 date in an 008 production event
        val productionLabelledDate = marc008dates.head.copy(
          label = firstEvent.dates.headOption
            .map(_.label)
            .getOrElse(marc008dates.head.label)
        )
        firstEvent.copy(dates = List(productionLabelledDate)) :: otherEvents
      case (Nil, _) => marc008productionEvents
      case _        => productionEvents.toList
    }
  }

  // Populate wwork:production from MARC tag 260.
  //
  // The rules are as follows:
  //
  //  - Populate "places" from subfield "a" and type as "Place"
  //  - Populate "agents" from subfield "b" and type as "Agent"
  //  - Populate "dates" from subfield "c" and type as "Period"
  //
  // If any of the following fields are included, we add them to the
  // existing places/agents/dates field, _and_ set the productionFunction
  // to "Manufacture":
  //
  //  - Extra places from subfield "e"
  //  - Extra agents from subfield "f"
  //  - Extra dates from subfield "g"
  //
  // If we don't have any of these fields, we can't tell what the
  // productionFunction is, so we should leave it as "None".
  //
  // Note: Regardless of their order in the MARC, these fields _always_
  // appear after a/b/c.  This is an implementation detail, not described
  // in the transform rules.
  // TODO: Check if this is okay.
  //
  // Note: a, b, c, e, f and g are all repeatable fields in the MARC spec.
  //
  // https://www.loc.gov/marc/bibliographic/bd260.html
  //
  private def getProductionFrom260Fields(
    record: MarcRecord
  ): Seq[ProductionEvent[IdState.Unminted]] =
    record.fieldsWithTags("260").map {
      field =>
        val label = labelFromSubFields(field)
        val places = placesFromSubfields(field, subfieldTag = "a")
        val agents = agentsFromSubfields(field, subfieldTag = "b")
        val dates = datesFromSubfields(field, subfieldTag = "c")

        val extraPlaces = placesFromSubfields(field, subfieldTag = "e")
        val extraAgents = agentsFromSubfields(field, subfieldTag = "f")
        val extraDates = datesFromSubfields(field, subfieldTag = "g")

        val productionFunction =
          if (extraPlaces != Nil || extraAgents != Nil || extraDates != Nil) {
            Some(Concept(label = "Manufacture"))
          } else None

        ProductionEvent(
          label = label,
          places = places ++ extraPlaces,
          agents = agents ++ extraAgents,
          dates = dates ++ extraDates,
          function = productionFunction
        )
    }

  // Populate wwork:production from MARC tag 264.
  //
  // The rules are as follows:
  //
  //  - Populate "places" from subfield "a" and type as "Place"
  //  - Populate "agents" from subfield "b" and type as "Agent"
  //  - Populate "dates" from subfield "c" and type as "Period"
  //
  // The production function is set based on the second indicator, as defined
  // in the MARC spec.
  //
  //  - 0 = Production
  //  - 1 = Publication
  //  - 2 = Distribution
  //  - 3 = Manufacture
  //
  // The MARC spec specifies another value for the production function:
  //
  //  - 4 = Copyright notice date
  //
  // We'll be putting copyright information in a separate part of the domain
  // model, so we drop any fields with indicator 4 for production events.
  //
  // Note that a, b and c are repeatable fields.
  //
  // https://www.loc.gov/marc/bibliographic/bd264.html
  //
  private def getProductionFrom264Fields(
    record: MarcRecord
  ) =
    record
      .fieldsWithTags("264")
      .filterNot {
        field =>
          field.indicator2.contains("4") || field.indicator2.contains(" ")
      }
      .map {
        field =>
          val label = labelFromSubFields(field)
          val places = placesFromSubfields(field, subfieldTag = "a")
          val agents = agentsFromSubfields(field, subfieldTag = "b")
          val dates = datesFromSubfields(field, subfieldTag = "c")

          val productionFunctionLabel = field.indicator2 match {
            case "0" => "Production"
            case "1" => "Publication"
            case "2" => "Distribution"
            case "3" => "Manufacture"
            case other =>
              throw CataloguingException(
                record = record,
                message =
                  s"Unrecognised second indicator for production function: [$other]"
              )
          }

          val productionFunction =
            Some(Concept(label = productionFunctionLabel))

          ProductionEvent(
            label = label,
            places = places,
            agents = agents,
            dates = dates,
            function = productionFunction
          )
      }

  private def getProductionFrom008(
    record: MarcRecord
  ): List[ProductionEvent[IdState.Unminted]] =
    record
      .controlField("008")
      .map(_.content)
      .flatMap(MarcProductionEventParser(_))
      .toList

  // @@AWLC: I'm joining these with a space because that seems more appropriate
  // given our catalogue, but the MARC spec isn't entirely clear on what to do.
  //
  // The convention used in the current Library website is to use a string.
  // Two examples, both retrieved 22 January 2019:
  //
  // bib 1548327:
  //    MARC        260    [Horsham] :|cCats Protection League,|c[ca.1990?]
  //    Website     [Horsham] : Cats Protection League, [ca.1990?]
  //
  // bib 2847879:
  //    MARC        264  0 [Netherne, Surrey],|c[ca. 1966]
  //    Website     [Netherne, Surrey], [ca. 1966]
  //
  private def labelFromSubFields(field: MarcField): String =
    field.subfields.map(_.content).mkString(" ")

  private def placesFromSubfields(
    field: MarcField,
    subfieldTag: String
  ): List[Place[IdState.Unminted]] =
    field
      .subfieldsWithTag(subfieldTag)
      .map(_.content)
      .map(Place(_).normalised)

  private def agentsFromSubfields(
    field: MarcField,
    subfieldTag: String
  ): List[Agent[IdState.Unminted]] =
    field
      .subfieldsWithTag(subfieldTag)
      .map(_.content)
      .map(Agent(_).normalised)

  private def datesFromSubfields(
    field: MarcField,
    subfieldTag: String
  ): List[Period[IdState.Unminted]] =
    field
      .subfieldsWithTag(subfieldTag)
      .map(_.content)
      .map(ParsedPeriod(_))
}
