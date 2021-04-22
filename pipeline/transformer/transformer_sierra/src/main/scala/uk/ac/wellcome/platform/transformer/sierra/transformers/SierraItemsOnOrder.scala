package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.sierra.SierraTransformable

/** This transformer creates catalogue items that correspond to items that
  * are "on order" or "awaiting cataloguing" -- which don't have their own
  * item record yet.
  *
  */
object SierraItemsOnOrder {
  def apply(transformable: SierraTransformable): List[Item[Unidentified]] = {
    List()
  }
}
