package weco.pipeline.transformer.tei

import org.apache.commons.io.IOUtils
import org.scalatest.EitherValues
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
  CollectionPath,
  DeletedReason,
  Format,
  Work,
  WorkData
}
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiDeletedMetadata}
import weco.pipeline.transformer.result.Result
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore

import java.nio.charset.StandardCharsets
import java.time.Instant

class TeiTransformerTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with InstantGenerators
    with S3ObjectLocationGenerators {

  it("transforms into a Work") {
    val modifiedTime = instantInLast30Days

    val work = transformToWork(filename = "/WMS_Arabic_1.xml")(
      id = "manuscript_15651",
      modifiedTime = modifiedTime
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.Tei,
      ontologyType = "Work",
      value = "manuscript_15651"
    )

    work.value shouldBe
      Work.Visible[Source](
        version = 1,
        data = WorkData[Unidentified](
          title = Some("Wellcome Library"),
          description =
            Some("1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath =
            Some(CollectionPath(path = "manuscript_15651", label = None))
        ),
        state = Source(sourceIdentifier, modifiedTime)
      )
  }

  it("extracts languages") {
    val work = transformToWork(filename = "/Javanese_4.xml")(
      id = "Wellcome_Javanese_4"
    )

    work.value.data.languages shouldBe List(
      Language(id = "jav", label = "Javanese"))
  }

  it("extracts inner Works") {
    val work = transformToWork(filename = "/Batak_36801.xml")(
      id = "Wellcome_Batak_36801"
    )

    work.value.state.internalWorkStubs should have size 12
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

  private def transformToWork(filename: String)(
    id: String,
    modifiedTime: Instant = instantInLast30Days): Result[Work[Source]] = {
    val teiXml = IOUtils.resourceToString(filename, StandardCharsets.UTF_8)

    val location = createS3ObjectLocation

    val transformer = new TeiTransformer(
      new MemoryStore[S3ObjectLocation, String](Map(location -> teiXml))
    )

    transformer(id, TeiChangedMetadata(location, modifiedTime), 1)
  }
}
