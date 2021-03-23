package weco.catalogue.pipeline_storage.generators

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import uk.ac.wellcome.elasticsearch.model.IndexId
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.pipeline_storage.Indexable

case class SampleDocument(
  version: Int,
  id: String,
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
    def id(document: SampleDocument): String = document.id
  }

  implicit val canonicalId: IndexId[SampleDocument] =
    (doc: SampleDocument) => doc.id

  implicit val encoder: Encoder[SampleDocument] = deriveEncoder

  implicit val decoder: Decoder[SampleDocument] = deriveDecoder
}

trait SampleDocumentGenerators extends RandomGenerators {
  def createDocument: SampleDocument =
    createDocumentWith()

  def createDocumentWith(
    id: String = randomAlphanumeric(),
    version: Int = randomInt(from = 1, to = 10)
  ): SampleDocument =
    SampleDocument(
      version = version,
      id = id,
      title = randomAlphanumeric()
    )
}
