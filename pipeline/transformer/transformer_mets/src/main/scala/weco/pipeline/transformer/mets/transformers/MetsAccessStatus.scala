package weco.pipeline.transformer.mets.transformers

import org.apache.commons.lang3.StringUtils.equalsIgnoreCase
import weco.catalogue.internal_model.locations.AccessStatus

object MetsAccessStatus {

  /** Access statuses are hand-entered in the source METS, so capitalisation
    * varies and carries no meaning. Compare with equalsIgnoreCase, as
    * MetsLicence already does for licences: it is locale-independent, unlike
    * lowercasing against the JVM's default locale.
    */
  def apply(
    accessConditionStatus: Option[String]
  ): Either[Throwable, Option[AccessStatus]] =
    accessConditionStatus match {
      // e.g. b21718969
      case Some(s) if equalsIgnoreCase(s, "Open") =>
        Right(Some(AccessStatus.Open))

      // e.g. b30468115 / b19912730
      case Some(s)
          if equalsIgnoreCase(s, "Open with advisory") || equalsIgnoreCase(
            s,
            "Requires registration"
          ) =>
        Right(Some(AccessStatus.OpenWithAdvisory))

      // e.g. b16469434 / b21072061
      case Some(s)
          if equalsIgnoreCase(s, "Restricted files") || equalsIgnoreCase(
            s,
            "Clinical images"
          ) =>
        Right(Some(AccessStatus.Restricted))

      // e.g. b16751875
      case Some(s) if equalsIgnoreCase(s, "Closed") =>
        Right(Some(AccessStatus.Closed))

      case None => Right(None)
      case Some(s) =>
        Left(new Throwable(s"Couldn't match $s to an access status"))
    }
}
