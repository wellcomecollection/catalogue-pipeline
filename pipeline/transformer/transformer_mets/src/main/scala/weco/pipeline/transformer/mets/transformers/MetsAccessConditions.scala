package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.{AccessStatus, License}
//
//trait MetsAccessConditions {
//  val accessStatus: Option[AccessStatus]
//  val licence: Option[License]
//  val usage: Option[String]
//}
case class MetsAccessConditions(
  accessStatus: Option[AccessStatus] = None,
  licence: Option[License] = None,
  usage: Option[String] = None
)
