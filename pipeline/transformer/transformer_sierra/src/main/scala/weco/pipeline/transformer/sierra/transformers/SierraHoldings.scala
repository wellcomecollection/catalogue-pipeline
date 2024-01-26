package weco.pipeline.transformer.sierra.transformers

import com.github.tototoshi.csv.CSVReader
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.LocationType.ClosedStores
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.{Holdings, Item}
import weco.catalogue.source_model.sierra.rules.SierraPhysicalLocationType
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraHoldingsData
import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  TypedSierraRecordNumber
}
import weco.sierra.models.marc.FixedField

import java.io.InputStream
import scala.io.Source

object SierraHoldings extends SierraQueryOps {
  type Output = List[Holdings]

  def apply(
    id: SierraBibNumber,
    holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]
  ): Output = {

    // We start by looking at fixed field 40, which contains a Sierra location code.
    // The value 'elro' tells us this is an online resource; if so, we create a series
    // of digital items.  Otherwise, we create a Holdings object.
    //
    // Note: the value in this field is padded to 5 spaces, so the magic string is "elro ",
    // not "elro".
    val (electronicHoldingsData, physicalHoldingsData) =
      holdingsDataMap
        .filterNot { case (_, data) => data.deleted || data.suppressed }
        .partition {
          case (_, holdingsData) =>
            holdingsData.fixedFields.get("40") match {
              case Some(FixedField(_, value, _)) if value.trim == "elro" => true
              case _                                                     => false
            }
        }

    val physicalHoldings =
      physicalHoldingsData.toList
        .flatMap {
          case (_, data) =>
            createPhysicalHoldings(id, data)
        }

    val digitalHoldings =
      electronicHoldingsData.toList
        .sortBy { case (id, _) => id.withCheckDigit }
        .map {
          case (id, data) =>
            (data.varFields, SierraElectronicResources(id, data.varFields))
        }
        .flatMap {
          case (varFields, items) =>
            items.map {
              it =>
                (varFields, it)
            }
        }
        .flatMap {
          case (varFields, Item(_, title, _, locations)) =>
            locations.map {
              loc =>
                Holdings(
                  note = title,
                  enumeration = SierraHoldingsEnumeration(id, varFields),
                  location = Some(loc)
                )
            }
        }

    // Note: holdings records are sparsely populated, and a lot of the information is
    // in fields we don't expose to the public.
    //
    // Since we also don't identify the Holdings objects we create, we may end up
    // with duplicates in the transformer output.  This isn't useful, so remove them.
    (deduplicateDigitalHoldings(digitalHoldings) ++ physicalHoldings).distinct
  }

  private def createPhysicalHoldings(
    id: TypedSierraRecordNumber,
    data: SierraHoldingsData
  ): Option[Holdings] = {

    // We take the description from field 866 subfield ǂa
    val description = data.varFields
      .filter { _.marcTag.contains("866") }
      .subfieldsWithTag("a")
      .map { _.content }
      .mkString(" ")

    // We take the note from field 866 subfield ǂz
    val note = data.varFields
      .filter { _.marcTag.contains("866") }
      .subfieldsWithTag("z")
      .map { _.content }
      .mkString(" ")

    // We prepend the description from 866 ǂa to the list of enumerations because
    // often this field contains the first line of the enumeration, and it simplifies
    // the display logic.  They were also presented together on wellcomelibrary.org.
    val enumeration =
      if (description.nonEmpty) {
        List(description) ++ SierraHoldingsEnumeration(id, data.varFields)
      } else {
        SierraHoldingsEnumeration(id, data.varFields)
      }

    val location = createPhysicalLocation(id, data)

    // We should only create the Holdings object if we have some interesting data
    // to include; otherwise we don't.
    val isNonEmpty = note.nonEmpty || enumeration.nonEmpty

    if (isNonEmpty) {
      Some(
        Holdings(
          note = if (note.nonEmpty) Some(note) else None,
          enumeration = enumeration,
          location = location
        )
      )
    } else {
      None
    }
  }

  private val stream: InputStream =
    getClass.getResourceAsStream("/location-types.csv")
  private val source = Source.fromInputStream(stream)
  private val csvReader = CSVReader.open(source)
  private val csvRows = csvReader.all()

  // location-types.csv is a list of 2-tuples, e.g.:
  //
  //    acqi,Info Service acquisitions
  //    acql,Wellcome Library
  //
  private val locationTypeMap: Map[String, String] = csvRows.map {
    row =>
      assert(row.size == 2)
      row.head -> row.last
  }.toMap

  private def createPhysicalLocation(
    id: TypedSierraRecordNumber,
    data: SierraHoldingsData
  ): Option[PhysicalLocation] =
    for {
      // We use the location code from fixed field 40.  If this is missing, we don't
      // create a location.
      //
      // Note: these values are padded to five spaces (e.g. "stax "), so we need
      // to remove whitespace first.
      code <- data.fixedFields.get("40").map { _.value.trim }
      name <- locationTypeMap.get(code)

      locationType <- SierraPhysicalLocationType.fromName(id, name)
      label = locationType match {
        case ClosedStores => ClosedStores.label
        case _            => name
      }

      // We take a shelfmark from field 949 ǂa, if present.
      //
      // Note: these values often contain extra whitespace (e.g. "/MED     "), so
      // we need to trim that off.
      shelfmark = data.varFields
        .filter { _.marcTag.contains("949") }
        .subfieldsWithTag("a")
        .map { _.content.trim }
        .distinct
        .headOption

      location = PhysicalLocation(
        locationType = locationType,
        label = label,
        shelfmark = shelfmark
      )
    } yield location

  // Sometimes the same URL will appear in multiple holdings records, with
  // different fields populated in each record.
  //
  // This affects ~1600 URLs.  We could fix this in the source data, but:
  //
  //    - that's a lot of records to fix by hand, and there are other data
  //      quality issues that need more attention
  //    - a lot of the holdings records are automatically ingested from
  //      publishers, and thus tricky to change
  //
  private def deduplicateDigitalHoldings(
    holdings: List[Holdings]
  ): List[Holdings] = {

    // These should all be holdings with digital locations; extracting this
    // information is so the compiler knows this further down.
    val locations =
      holdings.collect {
        case h @ Holdings(_, _, Some(location: DigitalLocation)) =>
          (h, location)
      }

    require(locations.size == holdings.size)

    // For each URL, we look at all the associated holdings and combine the holdings
    // if all the information is compatible.
    //
    // For this check, two values are considered compatible if:
    //
    //    - they are equal (e.g. Some("Connect to the database") and Some("Connect to the database"))
    //    - one is defined and the other is empty (e.g. Some("Connect to the database") and None)
    //
    val distinctUrls = locations.map {
      case (_, location) =>
        location.url
    }.distinct

    distinctUrls.flatMap {
      url =>
        val matchingHoldings = locations.filter {
          case (_, location) =>
            location.url == url
        }

        val notes = matchingHoldings
          .map { case (h, _) => h.note }
          .distinct
          .flatten

        val enumerations = matchingHoldings.map {
          case (h, _) =>
            h.enumeration
        }.distinct

        val linkTexts = matchingHoldings
          .map { case (_, loc) => loc.linkText }
          .distinct
          .flatten

        val uniqueNote = notes match {
          case Seq(n) => Right(Some(n))
          case Nil    => Right(None)
          case _      => Left(None)
        }

        val uniqueLinkText = linkTexts match {
          case Seq(text) => Right(Some(text))
          case Nil       => Right(None)
          case _         => Left(None)
        }

        (uniqueNote, enumerations, uniqueLinkText) match {
          case (Right(note), Seq(uniqueEnumerations), Right(linkText)) =>
            List(
              Holdings(
                note = note,
                enumeration = uniqueEnumerations,
                location = Some(
                  DigitalLocation(
                    url = url,
                    locationType = LocationType.OnlineResource,
                    linkText = linkText,
                    accessConditions = List(
                      AccessCondition(
                        method = AccessMethod.ViewOnline,
                        // Note: it's theoretically possible for an 856 URL to have the
                        // relationship "Related resources" -- see SierraElectronicResources --
                        // but at time of writing (Aug 2021), there are no holdings records
                        // where this is the case.
                        status = AccessStatus.LicensedResources(
                          relationship = LicensedResources.Resource
                        )
                      )
                    )
                  )
                )
              )
            )

          case _ => matchingHoldings.map { case (h, _) => h }
        }
    }
  }
}
