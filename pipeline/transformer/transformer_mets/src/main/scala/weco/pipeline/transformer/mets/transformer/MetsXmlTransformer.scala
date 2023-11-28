package weco.pipeline.transformer.mets.transformer

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.mets.{
  DeletedMetsFile,
  MetsFileWithImages,
  MetsSourceData
}
import weco.pipeline.transformer.Transformer
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
    transform(id, metsSourceData) match {
      case Right(metsData: MetsData) =>
        Right(
          metsData.toWork(
            version = metsSourceData.version,
            modifiedTime = metsSourceData.createdDate
          )
        )
      case Left(t) => Left(t)
    }

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
    InvisibleMetsData(root, root)
  }

  private def transformWithManifestations(
    root: MetsXml,
    manifestations: List[S3ObjectLocation]
  ): Result[InvisibleMetsData] = {
    getFirstManifestation(root, manifestations) match {
      case Right(filesRoot) =>
        InvisibleMetsData(root, filesRoot)
      case Left(t) => Left(t)
    }
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
