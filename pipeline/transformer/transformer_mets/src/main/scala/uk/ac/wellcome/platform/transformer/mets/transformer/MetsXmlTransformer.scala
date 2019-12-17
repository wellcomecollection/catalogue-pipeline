package uk.ac.wellcome.platform.transformer.mets.transformer

import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{Identified, ObjectLocation}
import uk.ac.wellcome.mets_adapter.models.MetsLocation

class MetsXmlTransformer(store: Readable[ObjectLocation, String]) {

  type Result[T] = Either[Throwable, T]

  def transform(metsLocation: MetsLocation): Result[MetsData] =
    getMetsXml(metsLocation.xmlLocation)
      .flatMap { root =>
        if (metsLocation.manifestations.isEmpty)
          transformWithoutManifestations(root)
        else
          transformWithManifestations(root, metsLocation.manifestationLocations)
      }

  private def transformWithoutManifestations(root: MetsXml): Result[MetsData] =
    for {
      id <- root.recordIdentifier
      accessCondition <- root.accessCondition
    } yield
      MetsData(
        recordIdentifier = id,
        accessCondition = accessCondition,
        thumbnailLocation = root.thumbnailLocation(id),
      )

  private def transformWithManifestations(
    root: MetsXml,
    manifestations: List[ObjectLocation]): Result[MetsData] =
    for {
      id <- root.recordIdentifier
      firstManifestation <- getFirstManifestation(root, manifestations)
      accessCondition <- firstManifestation.accessCondition
    } yield
      MetsData(
        recordIdentifier = id,
        accessCondition = accessCondition,
        thumbnailLocation = firstManifestation.thumbnailLocation(id),
      )

  private def getFirstManifestation(
    root: MetsXml,
    manifestations: List[ObjectLocation]): Result[MetsXml] =
    root.firstManifestationFilename
      .flatMap { name =>
        manifestations.find(_.path.endsWith(name)) match {
          case Some(location) => Right(location)
          case None =>
            Left(
              new Exception(
                s"Could not find manifestation with filename: $name")
            )
        }
      }
      .flatMap(getMetsXml)

  private def getMetsXml(location: ObjectLocation): Result[MetsXml] =
    store
      .get(location)
      .left
      .map(_.e)
      .flatMap { case Identified(_, xmlString) => MetsXml(xmlString) }
}
