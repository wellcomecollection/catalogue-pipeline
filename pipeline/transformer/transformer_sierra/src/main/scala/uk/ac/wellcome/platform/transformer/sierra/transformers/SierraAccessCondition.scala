package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.AccessCondition
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{SierraBibData, SierraBibNumber, SierraItemData, SierraItemNumber, SierraRulesForRequesting}

object SierraAccessCondition extends SierraQueryOps {
  def apply(bibId: SierraBibNumber, bibData: SierraBibData, itemId: SierraItemNumber, itemData: SierraItemData): List[AccessCondition] = {
    val bibAccessStatus = SierraAccessStatus.forBib(bibId, bibData)

    println(bibAccessStatus)
    println(itemData.holdCount)
    println(itemData.status)
    println(itemData.opacmsg)

    println(SierraRulesForRequesting(itemData))

    println(
      itemData.location.map { _.name }.map { SierraPhysicalLocationType.fromName(itemId, _) }
    )

    throw new RuntimeException(s"Unhandled case!")
    assert(false)
    List()
  }
}
