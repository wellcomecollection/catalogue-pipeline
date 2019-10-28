package uk.ac.wellcome.platform.transformer.mets

import uk.ac.wellcome.models.work.internal._

case class MetsData(
  recordIdentifier: String,
  accessCondition: Option[String]
) {

  def toWork(version: Int) =
    // TODO: change this to UnidentifiedInvisibleWork (UnidentifiedPartialWork?)
    UnidentifiedWork(
      title = "??? SHOULD TITLE BE OPTIONAL ???",
      sourceIdentifier = sourceIdentifier,
      version = version,
      items = List(item),
      mergeCandidates = List(mergeCandidate),
      otherIdentifiers = Nil,
    )

  private def item: MaybeDisplayable[Item] =
    Unidentifiable(
      agent = Item(
        locations = List(
          DigitalLocation(
            url = "???",
            locationType = LocationType("iiif-presentation"),
            license = accessCondition
              .map(_.toLowerCase)
              .map(License.createLicense),
          )
        )
      )
    )

  private def sourceIdentifier: SourceIdentifier =
    throw new NotImplementedError

  private def mergeCandidate: MergeCandidate =
    MergeCandidate(
      identifier = SourceIdentifier(
        identifierType = IdentifierType("sierra-system-number"),
        ontologyType = "Work",
        value = recordIdentifier
      )
    )
}
