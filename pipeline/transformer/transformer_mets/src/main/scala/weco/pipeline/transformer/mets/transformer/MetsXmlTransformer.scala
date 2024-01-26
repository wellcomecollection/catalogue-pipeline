package weco.pipeline.transformer.mets.transformer

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.mets.{DeletedMetsFile, MetsFileWithImages, MetsSourceData}
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
          metsData.toWork
        )
      case Left(t) => Left(t)
    }

  def transform(id: String, metsSourceData: MetsSourceData): Result[MetsData] =
    metsSourceData match {
      case DeletedMetsFile(createdDate, version) =>
        Right(
          DeletedMetsData(recordIdentifier = id, version, createdDate)
        )

      case metsFile @ MetsFileWithImages(
            _,
            _,
            manifestations,
            createdDate,
            version
          ) =>
        getMetsXml(metsFile.xmlLocation)
          .flatMap {
            root =>
              (manifestations match {
                case Nil => Right(root)
                case _ =>
                  getFirstManifestation(
                    root,
                    metsFile.manifestationLocations
                  )
              }) match {
                case Right(filesRoot) =>
                  InvisibleMetsData(root, filesRoot, version, createdDate)
                case Left(t) => Left(t)
              }
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
