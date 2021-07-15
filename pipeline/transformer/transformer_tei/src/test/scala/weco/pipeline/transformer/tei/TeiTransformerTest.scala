package weco.pipeline.transformer.tei

import org.apache.commons.io.IOUtils
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.generators.InstantGenerators
import weco.catalogue.internal_model.work.{
  DeletedReason,
  Format,
  Work,
  WorkData
}
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiDeletedMetadata}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore

import java.nio.charset.StandardCharsets

class TeiTransformerTest
    extends AnyFunSpec
    with Matchers
    with InstantGenerators
    with S3ObjectLocationGenerators {
  it("transforms into a Work") {
    val teiXml =
      IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8)
    val location = createS3ObjectLocation
    val store =
      new MemoryStore[S3ObjectLocation, String](Map(location -> teiXml))
    val transformer = new TeiTransformer(store)
    val timeModified = instantInLast30Days
    val id = "manuscript_15651"
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.Tei,
      ontologyType = "Work",
      value = id
    )
    transformer(id, TeiChangedMetadata(location, timeModified), 1) shouldBe Right(
      Work.Visible[Source](
        version = 1,
        data = WorkData[Unidentified](
          title = Some("Wellcome Library"),
          description =
            Some("1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037"),
          format = Some(Format.ArchivesAndManuscripts)
        ),
        state = Source(sourceIdentifier, timeModified)
      )
    )
  }

  it("extracts languages") {
    val teiXml =
      IOUtils.resourceToString("/Javanese_4.xml", StandardCharsets.UTF_8)
    val location = createS3ObjectLocation
    val store =
      new MemoryStore[S3ObjectLocation, String](Map(location -> teiXml))
    val transformer = new TeiTransformer(store)
    val timeModified = instantInLast30Days
    val id = "Wellcome_Javanese_4"
    transformer(id, TeiChangedMetadata(location, timeModified), 1).right.get.data.languages shouldBe List(
      Language("jv", "Javanese"))
  }

  it("handles delete messages") {

    val store =
      new MemoryStore[S3ObjectLocation, String](Map.empty)
    val transformer = new TeiTransformer(store)
    val timeModified = instantInLast30Days
    val id = "manuscript_15651"
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.Tei,
      ontologyType = "Work",
      value = id
    )
    transformer(id, TeiDeletedMetadata(timeModified), 1) shouldBe Right(
      Work.Deleted[Source](
        version = 1,
        state = Source(sourceIdentifier, timeModified),
        deletedReason = DeletedReason.DeletedFromSource("Deleted by TEI source")
      )
    )
  }
}
