package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.Format.ArchivesAndManuscripts
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.tei.generators.TeiDataGenerators
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.SierraIdentifierGenerators

import java.time.Instant

class TeiDataTest
    extends AnyFunSpec
    with SierraIdentifierGenerators
    with Matchers
    with IdentifiersGenerators
    with TeiDataGenerators {
  it("transforms into a work") {
    val title = "This is the title"
    val bnumber = createSierraBibNumber.withCheckDigit
    val description = Some("This is the description")
    val languages = List(Language("ara", "Arabic"))
    val languageNotes =
      List(Note(NoteType.LanguageNote, "Arabic with one line in Sanskrit"))
    val id = "id"
    val teiData = TeiData(
      id = id,
      title = title,
      bNumber = Some(bnumber),
      description = description,
      languages = languages,
      notes = languageNotes
    )

    val version = 1
    val modifiedTime = Instant.now()
    val work = teiData.toWork(modifiedTime, version)

    val mergeCandidate = MergeCandidate(
      createSierraSystemSourceIdentifierWith(value = bnumber),
      reason = "Bnumber present in TEI file"
    )
    val source = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.Tei,
        ontologyType = "Work",
        value = id
      ),
      sourceModifiedTime = modifiedTime,
      mergeCandidates = List(mergeCandidate)
    )
    work shouldBe Work.Visible[Source](
      version,
      WorkData[Unidentified](
        title = Some(title),
        description = description,
        languages = languages,
        format = Some(ArchivesAndManuscripts),
        collectionPath = Some(CollectionPath(path = id, label = None)),
        notes = languageNotes
      ),
      state = source
    )
  }

  it("does not create mergeCandidates if the bnumber is invalid") {
    val teiData = TeiData(
      id = "id",
      title = "This is the title",
      bNumber = Some("fjhsdg"),
      description = Some("This is the description"),
      languages = List(Language("ara", "Arabic"))
    )

    val work = teiData.toWork(Instant.now(), 1)

    work.state.mergeCandidates shouldBe empty
  }

  it("transforms authors in nestedData") {
    val contributors = List(
      Contributor(Person("John McClane"), List(ContributionRole("author")))
    )
    val firstInnerTeiData = TeiData(
      id = "id_1",
      title = "item title",
      contributors = contributors
    )
    val teiData = TeiData(
      id = "id",
      title = "work title",
      nestedTeiData = List(firstInnerTeiData)
    )

    val work = teiData.toWork(Instant.now(), 1)

    work.state.internalWorkStubs.head.workData.contributors shouldBe contributors
  }

  it("transforms multiple internal TeiData into internalWorks") {
    val firstInnerTeiData = TeiData(
      id = "id_1",
      title = "This is the first item title",
      description = Some("This is the first item description"),
      languages = List(Language("ara", "Arabic"))
    )
    val secondInnerTeiData = TeiData(
      id = "id_2",
      title = "This is the second item title",
      description = Some("This is the second item description"),
      languages = List(Language("ara", "Arabic"))
    )
    val teiData = TeiData(
      id = "id",
      title = "This is the title",
      bNumber = Some("fjhsdg"),
      description = Some("This is the description"),
      nestedTeiData = List(firstInnerTeiData, secondInnerTeiData)
    )

    val work = teiData.toWork(Instant.now(), 1)

    val firstInternalWorkStub = InternalWork.Source(
      sourceIdentifier =
        SourceIdentifier(IdentifierType.Tei, "Work", firstInnerTeiData.id),
      workData = WorkData(
        title = Some(firstInnerTeiData.title),
        languages = firstInnerTeiData.languages,
        description = firstInnerTeiData.description,
        format = Some(ArchivesAndManuscripts),
        collectionPath = Some(
          CollectionPath(
            path = s"${teiData.id}/${firstInnerTeiData.id}",
            label = None
          )
        )
      )
    )

    val secondInternalWorkStub = InternalWork.Source(
      sourceIdentifier =
        SourceIdentifier(IdentifierType.Tei, "Work", secondInnerTeiData.id),
      workData = WorkData(
        title = Some(secondInnerTeiData.title),
        languages = secondInnerTeiData.languages,
        description = secondInnerTeiData.description,
        format = Some(ArchivesAndManuscripts),
        collectionPath = Some(
          CollectionPath(
            path = s"${teiData.id}/${secondInnerTeiData.id}",
            label = None
          )
        )
      )
    )
    work.state.internalWorkStubs shouldBe List(
      firstInternalWorkStub,
      secondInternalWorkStub
    )
  }

  describe("setting the collectionPath") {
    it("uses the ID on a top-level Work") {
      val teiData = createTeiDataWith(id = "WMS_Example_1")

      val work = teiData.toWork(time = Instant.now(), version = 1)

      work.data.collectionPath shouldBe Some(
        CollectionPath(path = "WMS_Example_1", label = None)
      )
    }

    it("uses the ID for an absolute path on internal Works") {
      val teiData = createTeiDataWith(
        id = "WMS_Example_2",
        nestedTeiData = List(
          createTeiDataWith(id = "Part_1"),
          createTeiDataWith(id = "Part_2"),
          createTeiDataWith(id = "Part_3")
        )
      )

      val work = teiData.toWork(time = Instant.now(), version = 1)

      work.state.internalWorkStubs.map(_.workData.collectionPath) shouldBe List(
        Some(CollectionPath(path = "WMS_Example_2/Part_1", label = None)),
        Some(CollectionPath(path = "WMS_Example_2/Part_2", label = None)),
        Some(CollectionPath(path = "WMS_Example_2/Part_3", label = None))
      )
    }
  }

  it("flattens the nestedData into innerWorks") {
    val teiData = createTeiDataWith(
      id = "WMS_Example_2",
      nestedTeiData = List(
        createTeiDataWith(
          id = "Part_1",
          nestedTeiData = List(
            createTeiDataWith(id = "Part_1_1"),
            createTeiDataWith(
              id = "Part_1_2",
              nestedTeiData = List(createTeiDataWith(id = "Part_1_2_1"))
            )
          )
        ),
        createTeiDataWith(id = "Part_2")
      )
    )
    val work = teiData.toWork(time = Instant.now(), version = 1)
    work.state.internalWorkStubs
      .map(_.workData.collectionPath.get) should contain theSameElementsAs List(
      CollectionPath("WMS_Example_2/Part_1"),
      CollectionPath("WMS_Example_2/Part_1/Part_1_1"),
      CollectionPath("WMS_Example_2/Part_1/Part_1_2"),
      CollectionPath("WMS_Example_2/Part_1/Part_1_2/Part_1_2_1"),
      CollectionPath("WMS_Example_2/Part_2")
    )

  }

  it("copies the origin field into the production field") {
    val production = List(
      ProductionEvent(
        label = "ca.1732-63AD",
        places = Nil,
        agents = Nil,
        dates = List(ParsedPeriod("ca.1732-63AD"))
      )
    )
    val data = TeiData(id = "id", title = "title", origin = production)

    data.toWork(Instant.now, 1).data.production shouldBe production
  }

  describe("setting the languages") {
    describe("when the languages are only set on the top-level Work") {
      // This is based loosely on Tamil_1
      val languages = List(Language(id = "tam", label = "Tamil"))
      val teiData = createTeiDataWith(
        languages = languages,
        nestedTeiData = List(
          createTeiDataWith(languages = List()),
          createTeiDataWith(languages = List()),
          createTeiDataWith(languages = List())
        )
      )

      val work = teiData.toWork(time = Instant.now(), version = 1)

      it("keeps the languages on the top-level Work") {
        work.data.languages shouldBe languages
      }

      it("copies the languages to the internal Works") {
        work.state.internalWorkStubs.foreach {
          _.workData.languages shouldBe languages
        }
      }
    }

    describe("when the languages are only set on the internal Works") {
      // This is based loosely on MS_Malay_9
      val malay = Language(id = "mal", label = "Malay")
      val english = Language(id = "eng", label = "English")

      val innerLanguages = List(
        List(malay, english),
        List(malay),
        List(english)
      )

      val teiData = createTeiDataWith(
        languages = List(),
        nestedTeiData = innerLanguages.map {
          languages =>
            createTeiDataWith(languages = languages)
        }
      )

      val work = teiData.toWork(time = Instant.now(), version = 1)

      it("preserves the languages on the internal Works") {
        work.state.internalWorkStubs
          .map(_.workData.languages) shouldBe innerLanguages
      }

      // TODO: Would this be a useful thing to do?
      it("does not copy the union of the languages to the top-level Work") {
        work.data.languages shouldBe Nil
      }
    }
  }

  it("passes physical description into workdata") {
    val physicalDescription = Some("blah bluh")
    val data = TeiData(
      id = "id",
      title = "title",
      physicalDescription = physicalDescription
    )

    data
      .toWork(Instant.now, 1)
      .data
      .physicalDescription shouldBe physicalDescription
  }

  it("passes subjects into workData") {
    val subjects = List(
      Subject(label = "Botany", concepts = List(Concept(label = "Botany")))
    )
    val data = TeiData(id = "id", title = "title", subjects = subjects)
    data
      .toWork(Instant.now, 1)
      .data
      .subjects shouldBe subjects
  }
}
