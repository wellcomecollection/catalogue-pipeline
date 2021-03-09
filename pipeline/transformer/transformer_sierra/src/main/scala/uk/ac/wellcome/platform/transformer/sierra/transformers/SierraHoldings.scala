package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{Holdings, IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  SierraHoldingsData,
  SierraQueryOps
}
import weco.catalogue.sierra_adapter.models.{
  SierraBibNumber,
  SierraHoldingsNumber
}

object SierraHoldings extends SierraQueryOps {
  type Output = (List[Item[IdState.Unminted]], List[Holdings])

  def apply(id: SierraBibNumber, holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): Output = {

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
              case _ => false
            }
        }

    val physicalHoldings =
      physicalHoldingsData
        .toList
        .flatMap { case (_, data) =>
          createPhysicalHoldings(data)
        }

    val digitalItems: List[Item[IdState.Unminted]] =
      electronicHoldingsData
        .toList
        .sortBy { case (id, _) => id.withCheckDigit }
        .flatMap { case (id, data) =>
          SierraElectronicResources(id, data.varFields)
        }

    (digitalItems, physicalHoldings)
  }

  private def createPhysicalHoldings(data: SierraHoldingsData): Option[Holdings] = {

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

    // We should only create the Holdings object if we have some interesting data
    // to include; otherwise we don't.
    val isNonEmpty = description.nonEmpty || note.nonEmpty

    if (isNonEmpty) {
      Some(
        Holdings(
          description = if (description.nonEmpty) Some(description) else None,
          note = if (note.nonEmpty) Some(note) else None,
          enumeration = List()
        )
      )
    } else {
      None
    }
  }
}
