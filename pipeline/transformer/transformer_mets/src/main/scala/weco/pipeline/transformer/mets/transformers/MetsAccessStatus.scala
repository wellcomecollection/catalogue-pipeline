package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.AccessStatus

object MetsAccessStatus {
  def apply(
    accessConditionStatus: Option[String]
  ): Either[Throwable, Option[AccessStatus]] =
    accessConditionStatus match {
      // e.g. b21718969
      case Some(s) if s == "Open" => Right(Some(AccessStatus.Open))

      // e.g. b30468115 / b19912730
      case Some(s)
          if s == "Open with advisory" || s == "Requires registration" =>
        Right(Some(AccessStatus.OpenWithAdvisory))

      // e.g. b16469434 / b21072061
      case Some(s) if s == "Restricted files" || s == "Clinical images" =>
        Right(Some(AccessStatus.Restricted))

      // e.g. b16751875
      case Some(s) if s == "Closed" => Right(Some(AccessStatus.Closed))

      case None => Right(None)
      case Some(s) =>
        Left(new Throwable(s"Couldn't match $s to an access status"))
    }
}
