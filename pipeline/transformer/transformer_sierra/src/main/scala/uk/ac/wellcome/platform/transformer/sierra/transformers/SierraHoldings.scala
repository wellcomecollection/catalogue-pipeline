package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{Holdings, IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  SierraHoldingsData
}
import weco.catalogue.sierra_adapter.models.{
  SierraBibNumber,
  SierraHoldingsNumber
}

object SierraHoldings {
  type Output = (List[Item[IdState.Unminted]], List[Holdings])

  def apply(id: SierraBibNumber, holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): Output = {

    // We start by looking at fixed field 40, which contains a Sierra location code.
    // The value 'elro' tells us this is an online resource; if so, we create a series
    // of digital items.  Otherwise, we create a Holdings object.
    //
    // Note: the value in this field is padded to 5 spaces, so the magic string is "elro ",
    // not "elro".
    val (electronicHoldings, _) =
      holdingsDataMap
        .filterNot { case (_, data) => data.deleted || data.suppressed }
        .partition {
          case (_, holdingsData) =>
            holdingsData.fixedFields.get("40") match {
              case Some(FixedField(_, value)) if value.trim == "elro" => true
              case _ => false
            }
        }

    val digitalItems: List[Item[IdState.Unminted]] =
      electronicHoldings
        .toList
        .sortBy { case (id, _) => id.withCheckDigit }
        .flatMap { case (id, data) =>
          SierraElectronicResources(id, data.varFields)
        }

    (digitalItems, List())
  }
}
