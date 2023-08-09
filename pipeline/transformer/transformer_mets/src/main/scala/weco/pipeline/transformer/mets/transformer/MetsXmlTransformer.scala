package weco.pipeline.transformer.mets.transformer

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.mets.{
  DeletedMetsFile,
  MetsFileWithImages,
  MetsSourceData
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.mets.transformers.MetsTitle
import weco.pipeline.transformer.result.Result
import weco.storage.Identified
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Readable

class MetsXmlTransformer(store: Readable[S3ObjectLocation, String])
    extends Transformer[MetsSourceData] {

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
  ): Result[InvisibleMetsData] =
    for {
      id <- root.recordIdentifier
      title <- MetsTitle(root.root)
      accessConditionDz <- root.accessConditionDz
      accessConditionStatus <- root.accessConditionStatus
      accessConditionUsage <- root.accessConditionUsage
    } yield InvisibleMetsData(
      recordIdentifier = id,
      title = title,
      accessConditionDz = accessConditionDz,
      accessConditionStatus = accessConditionStatus,
      accessConditionUsage = accessConditionUsage,
      fileReferencesMapping = root.fileReferencesMapping(id),
      titlePageId = root.titlePageId
    )

  private def transformWithManifestations(
    root: MetsXml,
    manifestations: List[S3ObjectLocation]
  ): Result[InvisibleMetsData] =
    for {
      id <- root.recordIdentifier
      title <- MetsTitle(root.root)
      firstManifestation <- getFirstManifestation(root, manifestations)
      accessConditionDz <- firstManifestation.accessConditionDz
      accessConditionStatus <- firstManifestation.accessConditionStatus
      accessConditionUsage <- firstManifestation.accessConditionUsage
    } yield InvisibleMetsData(
      recordIdentifier = id,
      title = title,
      accessConditionDz = accessConditionDz,
      accessConditionStatus = accessConditionStatus,
      accessConditionUsage = accessConditionUsage,
      fileReferencesMapping = firstManifestation.fileReferencesMapping(id),
      titlePageId = firstManifestation.titlePageId
    )

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
