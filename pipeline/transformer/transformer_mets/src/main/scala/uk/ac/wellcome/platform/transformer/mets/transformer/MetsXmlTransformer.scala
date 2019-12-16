package uk.ac.wellcome.platform.transformer.mets.transformer

import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{ObjectLocation, Identified}
import uk.ac.wellcome.mets_adapter.models.MetsLocation

class MetsXmlTransformer(store: Readable[ObjectLocation, String]) {

  type Result[T] = Either[Throwable, T]

  def transform(metsLocation: MetsLocation): Result[MetsData] =
    for {
      root <- getMetsXml(metsLocation.xmlLocation)
      id <- root.recordIdentifier
      accessCondition <- root.accessCondition
    } yield
      MetsData(
        recordIdentifier = id,
        accessCondition = accessCondition,
        thumbnailLocation = root.thumbnailLocation(id),
      )

  private def getMetsXml(location: ObjectLocation): Result[MetsXml] =
    store
      .get(location)
      .left
      .map(_.e)
      .flatMap { case Identified(_, xmlString) => MetsXml(xmlString) }
}
