package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.locations.AccessStatus

object MetsAccessStatus {

  /** Access statuses are hand-entered in the source METS, so capitalisation
    * varies and carries no meaning. Match on the lowercased value, as
    * MetsLicence already does for licences.
    */
  def apply(
    accessConditionStatus: Option[String]
  ): Either[Throwable, Option[AccessStatus]] =
    accessConditionStatus match {
      case None => Right(None)
      case Some(status) =>
        status.toLowerCase match {
          // e.g. b21718969
          case "open" => Right(Some(AccessStatus.Open))

          // e.g. b30468115 / b19912730
          case "open with advisory" | "requires registration" =>
            Right(Some(AccessStatus.OpenWithAdvisory))

          // e.g. b16469434 / b21072061
          case "restricted files" | "clinical images" =>
            Right(Some(AccessStatus.Restricted))

          // e.g. b16751875
          case "closed" => Right(Some(AccessStatus.Closed))

          case _ =>
            Left(new Throwable(s"Couldn't match $status to an access status"))
        }
    }
}
