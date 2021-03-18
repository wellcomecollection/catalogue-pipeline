package uk.ac.wellcome.platform.transformer.sierra.transformers

import com.github.tototoshi.csv.CSVReader
import weco.catalogue.internal_model.locations.LocationType.ClosedStores
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  SierraHoldingsData,
  SierraQueryOps
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.PhysicalLocation
import weco.catalogue.internal_model.work.{Holdings, Item}
import weco.catalogue.sierra_adapter.models.{
  SierraBibNumber,
  SierraHoldingsNumber,
  TypedSierraRecordNumber
}

import java.io.InputStream
import scala.io.Source

object SierraHoldings extends SierraQueryOps {
  type Output = (List[Item[IdState.Unminted]], List[Holdings])

  def apply(
    id: SierraBibNumber,
    holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): Output = {

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
              case Some(FixedField(_, value)) if value.trim == "elro" => true
              case _                                                  => false
            }
        }

    val physicalHoldings =
      physicalHoldingsData.toList
        .flatMap {
          case (_, data) =>
            createPhysicalHoldings(id, data)
        }

    val digitalItems: List[Item[IdState.Unminted]] =
      electronicHoldingsData.toList
        .sortBy { case (id, _) => id.withCheckDigit }
        .flatMap {
          case (id, data) =>
            SierraElectronicResources(id, data.varFields)
        }

    // Note: holdings records are sparsely populated, and a lot of the information is
    // in fields we don't expose to the public.
    //
    // Since we also don't identify the Holdings objects we create, we may end up
    // with duplicates in the transformer output.  This isn't useful, so remove them.
    (digitalItems, physicalHoldings.distinct)
  }

  private def createPhysicalHoldings(
    id: TypedSierraRecordNumber,
    data: SierraHoldingsData): Option[Holdings] = {

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

    val location = createLocation(id, data)

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
  private val locationTypeMap: Map[String, String] = csvRows.map { row =>
    assert(row.size == 2)
    row.head -> row.last
  }.toMap

  private def createLocation(
    id: TypedSierraRecordNumber,
    data: SierraHoldingsData): Option[PhysicalLocation] =
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
}
