package weco.catalogue.pipeline_storage.generators

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import uk.ac.wellcome.elasticsearch.model.IndexId
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.pipeline_storage.Indexable

case class SampleDocument(
  version: Int,
  canonicalId: String,
  title: String,
  data: SampleDocumentData = SampleDocumentData()
)

case class SampleDocumentData(
  genre: Option[String] = None,
  date: Option[String] = None,
)

object SampleDocument {
  implicit val indexable = new Indexable[SampleDocument] {
    def version(document: SampleDocument): Long = document.version
    def id(document: SampleDocument): String = document.canonicalId
  }

  implicit val canonicalId: IndexId[SampleDocument] =
    (doc: SampleDocument) => doc.canonicalId

  implicit val encoder: Encoder[SampleDocument] = deriveEncoder

  implicit val decoder: Decoder[SampleDocument] = deriveDecoder
}

trait SampleDocumentGenerators extends RandomGenerators {
  def createSampleDocument: SampleDocument =
    createSampleDocumentWith()

  def createSampleDocumentWith(
    version: Int = randomInt(from = 1, to = 10),
    canonicalId: String = randomAlphanumeric()
  ): SampleDocument =
    SampleDocument(
      version = version,
      canonicalId = canonicalId,
      title = randomAlphanumeric()
    )
}
