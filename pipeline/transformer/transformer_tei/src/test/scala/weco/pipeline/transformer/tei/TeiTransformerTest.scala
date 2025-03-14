package weco.pipeline.transformer.tei

import org.apache.commons.io.IOUtils
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.IdState.{
  Identifiable,
  Unminted
}
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.generators.InstantGenerators
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiDeletedMetadata}
import weco.pipeline.transformer.result.Result
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
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

    val contributors: List[Contributor[Unminted]] = List(
      Contributor(
        Person(
          label = """ابو على الحسين ابن عبد الله ابن
                  سينا""",
          id = Identifiable(
            SourceIdentifier(
              IdentifierType.Fihrist,
              "Person",
              "person_97166546"
            )
          )
        ),
        roles = List(ContributionRole("author"))
      )
    )
    work.value shouldBe
      Work.Visible[Source](
        version = 1,
        data = WorkData[Unidentified](
          title = Some("MS_Arabic_1"),
          description =
            Some("1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037"),
          format = Some(Format.ArchivesAndManuscripts),
          collectionPath = Some(
            CollectionPath(
              path = "manuscript_15651",
              label = Some("MS_Arabic_1")
            )
          ),
          subjects = List(
            Subject(
              id = Identifiable(
                SourceIdentifier(
                  IdentifierType.LCSubjects,
                  "Concept",
                  "sh85083116"
                )
              ),
              label = "Medicine, Arab",
              concepts = List(Concept(label = "Medicine, Arab"))
            )
          ),
          notes = List(
            Note(
              contents =
                "Script: Beautiful Naskh Ink: Black; headings and dividers highlighted in red",
              noteType = NoteType.HandNote
            )
          ),
          physicalDescription = Some(
            "Oriental paper of two colours: 'beige' and 'biscuit' and thickness of 0.10 mm.; Material: chart; 529 ff.; leaf dimensions: width 215mm, height 336mm; written dimensions: width 125mm, height 220mm"
          )
        ),
        state = Source(
          sourceIdentifier,
          modifiedTime,
          internalWorkStubs = List(
            InternalWork.Source(
              sourceIdentifier = SourceIdentifier(
                identifierType = IdentifierType.Tei,
                ontologyType = "Work",
                value = "MS_Arabic_1-item1"
              ),
              workData = WorkData[Unidentified](
                title = Some("MS_Arabic_1 item 1"),
                alternativeTitles =
                  List("Al-Qānūn fī al-ṭibb", "كتاب القانون في الطبّ"),
                languages = List(Language("ara", "Arabic")),
                notes = List(
                  Note(
                    contents =
                      "فهذا اخر الكلام من الكتاب الثالث وقد استوفينا الكلام منه حسب ما يليق بذلك وعلينا ان نشرع الان فى الكتاب الرابع حامدين لله تعالى",
                    noteType = NoteType.ColophonNote
                  ),
                  Note(
                    contents =
                      "Fol. 2b.1: بسم اللّه الرحمن الرحيم وبه نستعين ونتوكل عليه",
                    noteType = NoteType.BeginsNote
                  ),
                  Note(
                    contents =
                      "Fol. 2b.2: والحمد للّه حمدًا يستحقه بعلو شانه وشبوع احسانه",
                    noteType = NoteType.BeginsNote
                  ),
                  Note(
                    contents =
                      "Fol. 2b.3: وبعد وقد التمس منى بعض خلص اخواني ومن يلزمنى اسعافه فيما يسح به وسعى ان اضف فى الطب كتابًا مشتملًا على قوانينه الكلية والجزئية اشتمالًا",
                    noteType = NoteType.BeginsNote
                  ),
                  Note(
                    contents =
                      "Fol. 528a.12: فصل فى انتفاخ الاظفار والحكة فيها تعالج بما البحر غلسا دايما فيزول به وبطبيخ العدس والكرسنه او بطبيخ الخنثى ومن اضمدته البنبوس والزفت والتين الاصفر المطبوخ مجموعة وفرادى",
                    noteType = NoteType.EndsNote
                  )
                ),
                collectionPath = Some(
                  CollectionPath(path = "manuscript_15651/MS_Arabic_1-item1")
                ),
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

  it("extracts languages with different labels to MARC ") {
    val work = transformToWork(filename = "/Batak_36801.xml")(
      id = "Wellcome_Batak_36801"
    )
    // The document contains <textLang mainLang="bbc">Toba-Batak</textLang>
    // The MARC code for that language is btk and the MARC label is Batak
    // The extraction retains the label from the TEI document, but replaces the code
    work.value.data.languages shouldBe List(
      Language(id = "btk", label = "Toba-Batak")
    )
  }

  it("extracts languageNotes if it cannot parse the languages") {
    val work = transformToWork(filename = "/Indic_Alpha_978.xml")(
      id = "Wellcome_Alpha_978"
    )

    work.value.data.languages shouldBe Nil
    work.value.data.notes should contain(
      Note(
        NoteType.LanguageNote,
        "Sanskrit, with additional title entries in Persian script."
      )
    )
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

  private val MS_MSL_112_work = transformToWork(filename = "/MS_MSL_112.xml")(
    id = "Greek_MS_MSL_112"
  ).value

  it("extracts msItems within msItems") {

    val internalWorkStubs = MS_MSL_112_work.state.internalWorkStubs
    internalWorkStubs should have size 5
    internalWorkStubs.map(
      _.workData.title.get
    ) should contain theSameElementsAs List(
      " Medical Epitome - 3, first part of 6, 4, 5 ",
      "Περὶ θεραπευτικ(ῶν) μεθόδ(ων) βιβλίον πρῶτον",
      "Τοῦ αὐτοῦ περὶ θεραπείας παθῶν καὶ τῶν ἔξωθεν φαρμάκων",
      "Τοῦ αὐτοῦ περὶ θεραπευτικῆς μεθόδου τῶν κατὰ μέρος παθῶν βιβλίον δεύτερον",
      "Τοῦ αὐτοῦ περὶ συνθέσεως φαρμάκων λόγος Α ́"
    )
  }

  it("extracts provenance information") {
    // https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-provenance.html
    MS_MSL_112_work.data.notes should contain allOf (
      Note(
        NoteType.OwnershipNote,
        "Thought to have been newly excecuted when Anthony Askew acquired it"
      ),
      Note(
        NoteType.OwnershipNote,
        "Marks of ownership flyleaf IIrEx Bibliotheca Askeviana / Part ii. Art. 541 / J. Sims"
      )
    )
  }
  it("extracts acquisition information") {
    // https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-acquisition.html
    MS_MSL_112_work.data.notes should contain(
      Note(
        NoteType.AcquisitionNote,
        "Anthony Askew(1722–74)London; his sale at G. Leigh and J. Sotheby London 15 March 1785 , lot 541. Purchased by James Sims (1741–1820)London, in 1785. Purchased by the London Medical Society in 1802. Purchased by the Wellcome Library in 1984."
      )
    )

  }

  private val MS_MSL_114_work = transformToWork(filename = "/MS_MSL_114.xml")(
    id = "MS_MSL_114"
  ).value

  it("extracts authors") {

    MS_MSL_114_work.state.internalWorkStubs.head.workData.contributors shouldBe List(
      Contributor(
        Person(
          label = "Paul of Aegina",
          id = Identifiable(
            SourceIdentifier(IdentifierType.VIAF, "Person", "person_84812936")
          )
        ),
        roles = List(ContributionRole("author"))
      )
    )
  }

  it("extracts hand description information") {
    // https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-handDesc.html

    MS_MSL_114_work.data.notes should contain allOf (
      Note(
        NoteType.HandNote,
        "Attributed by Nigel Wilson to Georgios Chrysokokkes (RGK III 126)."
      ),
      Note(
        NoteType.HandNote,
        "Several different hands have added text throughout the manuscript."
      )
    )
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
