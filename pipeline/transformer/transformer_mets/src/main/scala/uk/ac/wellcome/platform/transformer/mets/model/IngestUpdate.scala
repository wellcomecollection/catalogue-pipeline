package uk.ac.wellcome.platform.transformer.mets.model

case class IngestUpdate(space: Space, bag: Bag)

case class Space(id: String)
case class Bag(info: BagInfo)
case class BagInfo(externalIdentifier: String)
