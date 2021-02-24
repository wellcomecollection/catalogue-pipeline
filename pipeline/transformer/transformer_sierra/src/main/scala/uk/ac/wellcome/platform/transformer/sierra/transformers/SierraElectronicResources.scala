package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.{IdState, Item}
import uk.ac.wellcome.platform.transformer.sierra.source.{SierraQueryOps, VarField}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

// Create items with a DigitalLocation based on the contents of field 856.
//
// The 856 field is used to link to external resources, and it has a variety
// of uses at Wellcome.  Among other things, it links to websites, electronic
// journals, and links to canned searches in our catalogue.
//
// See RFC 035 Modelling MARC 856 "web linking entry"
// https://github.com/wellcomecollection/docs/pull/48
//
// TODO: Update this link to the published version of the RFC
//
object SierraElectronicResources extends SierraQueryOps with Logging {
  def apply(bibId: SierraBibNumber, varFields: List[VarField]): List[Item[IdState.Unminted]] =
    varFields
      .filter { _.marcTag.contains("856") }
      .flatMap(createItem)

  private def createItem(vf: VarField): Option[Item[IdState.Unminted]] = {
    assert(vf.marcTag.contains("856"))

    None
  }
}
