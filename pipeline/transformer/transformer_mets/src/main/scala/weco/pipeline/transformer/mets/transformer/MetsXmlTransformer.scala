package weco.pipeline.transformer.mets.transformer

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.mets.{
  DeletedMetsFile,
  MetsFileWithImages,
  MetsSourceData
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.mets.transformer.models.MetsAccessConditions
import weco.pipeline.transformer.mets.transformers.MetsTitle
import weco.pipeline.transformer.result.Result
import weco.storage.Identified
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Readable

class MetsXmlTransformer(store: Readable[S3ObjectLocation, String])
    extends Transformer[MetsSourceData]
    with Logging {

  override def apply(
    id: String,
    metsSourceData: MetsSourceData,
    version: Int
  ): Result[Work[WorkState.Source]] =
    for {
      metsData <- transform(id, metsSourceData)
      work <- metsData.toWork(
        version = metsSourceData.version,
        modifiedTime = metsSourceData.createdDate
      )
    } yield work

  def transform(id: String, metsSourceData: MetsSourceData): Result[MetsData] =
    metsSourceData match {
      case DeletedMetsFile(_, _) =>
        Right(
          DeletedMetsData(recordIdentifier = id)
        )

      case metsFile @ MetsFileWithImages(_, _, manifestations, _, _) =>
        getMetsXml(metsFile.xmlLocation)
          .flatMap {
            root =>
              if (manifestations.isEmpty) {
                transformWithoutManifestations(root)
              } else {
                transformWithManifestations(
                  root,
                  metsFile.manifestationLocations
                )
              }
          }
    }

  private def transformWithoutManifestations(
    root: MetsXml
  ): Result[InvisibleMetsData] = {
    val accessConditions = MetsAccessConditions(root.root)
    for {
      id <- root.recordIdentifier
      title <- MetsTitle(root.root)
    } yield InvisibleMetsData(
      recordIdentifier = id,
      title = title,
      accessConditionDz = accessConditions.dz,
      accessConditionStatus = accessConditions.status,
      accessConditionUsage = accessConditions.usage,
      fileReferencesMapping = root.fileReferencesMapping(id),
      thumbnailReference = root.thumbnailReference
    )
  }

  private def transformWithManifestations(
    root: MetsXml,
    manifestations: List[S3ObjectLocation]
  ): Result[InvisibleMetsData] = {
    // a METS file without separate manifestations is its own manifestation,
    // otherwise, the first one is treated as representative of the whole.
//    val referenceManifestation = getFirstManifestation(root, manifestations).getOrElse(root)
//
//    // If there are manifestation files in the bag, then they should be referenced in the main METS file
//    // similarly, if there are MultipleManifestations in the METS file, then they should be in the bag.
//    (manifestations, referenceManifestation) match {
//      case (Nil, manifestation) if manifestation != root =>
//
//      case (_, manifestation) if manifestation == root =>
//    }
    for {
      id <- root.recordIdentifier
      title <- MetsTitle(root.root)
      referenceManifestation <- getFirstManifestation(root, manifestations)
      accessConditions <- Right(
        MetsAccessConditions(referenceManifestation.root)
      )
    } yield InvisibleMetsData(
      recordIdentifier = id,
      title = title,
      accessConditionDz = accessConditions.dz,
      accessConditionStatus = accessConditions.status,
      accessConditionUsage = accessConditions.usage,
      fileReferencesMapping = referenceManifestation.fileReferencesMapping(id),
      thumbnailReference = referenceManifestation.thumbnailReference
    )
  }

  private def getFirstManifestation(
    root: MetsXml,
    manifestations: List[S3ObjectLocation]
  ): Result[MetsXml] =
    root.firstManifestationFilename
      .flatMap {
        name =>
          manifestations.find(
            loc => loc.key.endsWith(name) || loc.key.endsWith(s"$name.xml")
          ) match {
            case Some(location) => Right(location)
            case None =>
              Left(
                new Exception(
                  s"Could not find manifestation with filename: $name"
                )
              )
          }
      }
      .flatMap(getMetsXml)

  private def getMetsXml(location: S3ObjectLocation): Result[MetsXml] =
    store
      .get(location)
      .left
      .map(_.e)
      .flatMap { case Identified(_, xmlString) => MetsXml(xmlString) }
}
