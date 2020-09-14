package uk.ac.wellcome.platform.transformer.miro

import scala.util.Try

import grizzled.slf4j.Logging

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.exceptions.ShouldNotTransformException
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.platform.transformer.miro.transformers._
import WorkState.Unidentified

class MiroRecordTransformer
    extends MiroContributors
    with MiroCreatedDate
    with MiroItems
    with MiroGenres
    with MiroImage
    with MiroIdentifiers
    with MiroSubjects
    with MiroThumbnail
    with MiroTitleAndDescription
    with MiroWorkType
    with Logging {

  def transform(miroRecord: MiroRecord,
                miroMetadata: MiroMetadata,
                version: Int): Try[Work[Unidentified]] =
    doTransform(miroRecord, miroMetadata, version) map { transformed =>
      debug(s"Transformed record to $transformed")
      transformed
    } recover {
      case e: Throwable =>
        error("Failed to perform transform to unified item", e)
        throw e
    }

  private def doTransform(originalMiroRecord: MiroRecord,
                          miroMetadata: MiroMetadata,
                          version: Int): Try[Work[Unidentified]] = {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("miro-image-number"),
      ontologyType = "Work",
      value = originalMiroRecord.imageNumber
    )

    Try {
      // Any records that aren't cleared for the Catalogue API should be
      // discarded immediately.
      if (!miroMetadata.isClearedForCatalogueAPI) {
        throw new ShouldNotTransformException(
          s"Image ${originalMiroRecord.imageNumber} is not cleared for the API!"
        )
      }

      // This is an utterly awful hack we have to live with until we get
      // these corrected in the source data.
      val miroRecord = MiroRecord.create(toJson(originalMiroRecord).get)

      // These images should really have been removed from the pipeline
      // already, but we have at least one instance (B0010525).  It was
      // throwing a MatchError when we tried to pick a license, so handle
      // it properly here.
      if (!miroRecord.copyrightCleared.contains("Y")) {
        throw new ShouldNotTransformException(
          s"Image ${miroRecord.imageNumber} does not have copyright clearance!"
        )
      }

      val (title, description) = getTitleAndDescription(miroRecord)

      val data = WorkData[WorkState.Unidentified, IdState.Identifiable](
        otherIdentifiers = getOtherIdentifiers(miroRecord),
        title = Some(title),
        workType = getWorkType,
        description = description,
        lettering = miroRecord.suppLettering,
        createdDate = getCreatedDate(miroRecord),
        subjects = getSubjects(miroRecord),
        genres = getGenres(miroRecord),
        contributors = getContributors(miroRecord),
        thumbnail = Some(getThumbnail(miroRecord)),
        items = getItems(miroRecord),
        images = List(getImage(miroRecord, version))
      )

      Work.Standard[Unidentified](version, Unidentified(sourceIdentifier), data)
    }.recover {
      case e: ShouldNotTransformException =>
        debug(s"Should not transform: ${e.getMessage}")
        Work.Invisible[Unidentified](
          state = Unidentified(sourceIdentifier),
          version = version,
          data = WorkData()
        )
    }
  }
}
