package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation,
  License,
  LocationType
}

object MetsLocation {
  def apply(
    recordIdentifier: String,
    license: Option[License],
    accessStatus: Option[AccessStatus],
    accessConditionUsage: Option[String]
  ): DigitalLocation =
    DigitalLocation(
      url =
        s"https://iiif.wellcomecollection.org/presentation/v2/$recordIdentifier",
      locationType = LocationType.IIIFPresentationAPI,
      license = license,
      accessConditions = accessConditions(accessStatus, accessConditionUsage)
    )

  private def accessConditions(
    accessStatus: Option[AccessStatus],
    accessConditionUsage: Option[String]
  ): List[AccessCondition] =
    (accessStatus, accessConditionUsage) match {
      case (None, None) => Nil
      case _ =>
        List(
          AccessCondition(
            method = AccessMethod.ViewOnline,
            status = accessStatus,
            terms = accessConditionUsage
          )
        )
    }
}
