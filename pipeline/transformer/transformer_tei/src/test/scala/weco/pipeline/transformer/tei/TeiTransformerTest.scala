package weco.pipeline.transformer.tei

import org.apache.commons.io.IOUtils
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.IdState.{Identifiable, Unminted}
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.generators.InstantGenerators
import weco.catalogue.internal_model.work._
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

    val contributors: List[Contributor[Unminted]] = List(Contributor(Person(label =
      """ابو على الحسين ابن عبد الله ابن
                  سينا""", id = Identifiable(SourceIdentifier(IdentifierType.Fihrist, "Person", "person_97166546"))), roles = List(ContributionRole("author"))))
    work.value shouldBe
      Work.Visible[Source](
        version = 1,
        data = WorkData[Unidentified](
          title = Some("MS_Arabic_1"),
          description =
            Some("1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath =
            Some(CollectionPath(path = "manuscript_15651", label = None))
        ),
        state = Source(
          sourceIdentifier,
          modifiedTime,
          internalWorkStubs = List(
            InternalWork.Source(
              sourceIdentifier = SourceIdentifier(
                IdentifierType.Tei,
                "Work",
                "MS_Arabic_1-item1"
              ),
              workData = WorkData[Unidentified](
                title = Some("MS_Arabic_1 item 1"),
                languages = List(Language("ara", "Arabic")),
                collectionPath =
                  Some(CollectionPath("manuscript_15651/MS_Arabic_1-item1")),
                format = Some(Format.ArchivesAndManuscripts),
                contributors = contributors
              )
            )
          )
        )
      )
  }

  it("extracts languages") {
    val work = transformToWork(filename = "/Javanese_4.xml")(
      id = "Wellcome_Javanese_4"
    )

    work.value.data.languages shouldBe List(
      Language(id = "jav", label = "Javanese")
    )
  }

  it("extracts languageNotes if it cannot parse the languages") {
    val work = transformToWork(filename = "/Indic_Alpha_978.xml")(
      id = "Wellcome_Alpha_978"
    )

    work.value.data.languages shouldBe Nil
    work.value.data.notes shouldBe List(
      Note(
        NoteType.LanguageNote,
        "Sanskrit, with additional title entries in Persian script."))
  }

  it("extracts msItem inner Works") {
    val work = transformToWork(filename = "/Batak_36801.xml")(
      id = "Wellcome_Batak_36801"
    )

    work.value.state.internalWorkStubs should have size 12
  }

  it("extracts msPart inner Works") {
    val work = transformToWork(filename = "/Wellcome_MS_Malay_7.xml")(
      id = "Wellcome_Malay_7"
    )

    val internalWorkStubs = work.value.state.internalWorkStubs
    internalWorkStubs should have size 15
    internalWorkStubs.head.workData.description shouldBe Some(
      "Lists of plants, roots, woods, fibres, snakes, animals and insects."
    )
  }

  it("extracts msItems within msItems") {
    val work = transformToWork(filename = "/MS_MSL_112.xml")(
      id = "Greek_MS_MSL_112"
    )

    val internalWorkStubs = work.value.state.internalWorkStubs
    internalWorkStubs should have size 5
    internalWorkStubs.map(_.workData.title.get) should contain theSameElementsAs List(
      " Medical Epitome - 3, first part of 6, 4, 5 ",
      "Περὶ θεραπευτικ(ῶν) μεθόδ(ων) βιβλίον πρῶτον",
      "Τοῦ αὐτοῦ περὶ θεραπείας παθῶν καὶ τῶν ἔξωθεν φαρμάκων",
      "Τοῦ αὐτοῦ περὶ θεραπευτικῆς μεθόδου τῶν κατὰ μέρος παθῶν βιβλίον δεύτερον",
      "Τοῦ αὐτοῦ περὶ συνθέσεως φαρμάκων λόγος Α ́"
    )
  }

  it("extracts authors"){
    val work = transformToWork(filename = "/MS_MSL_114.xml")(
      id = "MS_MSL_114"
    )

    work.value.state.internalWorkStubs.head.workData.contributors shouldBe List(Contributor(Person(label = "Paul of Aegina", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "person_84812936"))), roles = List(ContributionRole("author"))))
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
    modifiedTime: Instant = instantInLast30Days
  ): Result[Work[Source]] = {
    val teiXml = IOUtils.resourceToString(filename, StandardCharsets.UTF_8)

    val location = createS3ObjectLocation

    val transformer = new TeiTransformer(
      new MemoryStore[S3ObjectLocation, String](Map(location -> teiXml))
    )

    transformer(id, TeiChangedMetadata(location, modifiedTime), 1)
  }
}
