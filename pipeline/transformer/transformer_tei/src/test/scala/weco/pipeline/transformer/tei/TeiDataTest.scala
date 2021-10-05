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
import weco.catalogue.internal_model.work.{
  CollectionPath,
  InternalWork,
  MergeCandidate,
  Work,
  WorkData
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.pipeline.transformer.tei.generators.TeiDataGenerators
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
    val id = "id"
    val teiData = TeiData(
      id = id,
      title = title,
      bNumber = Some(bnumber),
      description = description,
      languages = languages
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
        collectionPath = Some(CollectionPath(path = id, label = None))
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
      nestedTeiData = Right(List(firstInnerTeiData, secondInnerTeiData))
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
            label = None))
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
            label = None))
      )
    )
    work.state.internalWorkStubs shouldBe List(
      firstInternalWorkStub,
      secondInternalWorkStub)
  }

  it("uses only toplevel data if it fails extracting nested data") {

    val title = "This is the top-level title"
    val teiData = TeiData(
      id = "id",
      title = title,
      nestedTeiData = Left(new RuntimeException("Boom"))
    )

    val work = teiData.toWork(Instant.now(), 1)

    work.state.internalWorkStubs shouldBe empty
    work.data.title shouldBe Some(title)
  }

  describe("setting the collectionPath") {
    it("uses the ID on a top-level Work") {
      val teiData = createTeiDataWith(id = "WMS_Example_1")

      val work = teiData.toWork(time = Instant.now(), version = 1)

      work.data.collectionPath shouldBe Some(
        CollectionPath(path = "WMS_Example_1", label = None))
    }

    it("uses the ID for an absolute path on internal Works") {
      val teiData = createTeiDataWith(
        id = "WMS_Example_2",
        nestedTeiData = Right(
          List(
            createTeiDataWith(id = "Part_1"),
            createTeiDataWith(id = "Part_2"),
            createTeiDataWith(id = "Part_3"),
          )
        )
      )

      val work = teiData.toWork(time = Instant.now(), version = 1)

      work.state.internalWorkStubs.map(_.workData.collectionPath) shouldBe List(
        Some(CollectionPath(path = "WMS_Example_2/Part_1", label = None)),
        Some(CollectionPath(path = "WMS_Example_2/Part_2", label = None)),
        Some(CollectionPath(path = "WMS_Example_2/Part_3", label = None)),
      )
    }
  }

  describe("if there's a single inner data") {
    it("uses the title of the item") {
      val innerTeiData = TeiData(
        id = "id_1",
        title = "This is the item title"
      )
      val teiData = TeiData(
        id = "id",
        title = "This is the top-level title",
        nestedTeiData = Right(List(innerTeiData))
      )

      val work = teiData.toWork(Instant.now(), 1)

      work.state.internalWorkStubs shouldBe empty
      work.data.title shouldBe Some(innerTeiData.title)
    }
  }

  describe("setting the languages") {
    describe("when the languages are only set on the top-level Work") {
      // This is based loosely on Tamil_1
      val languages = List(Language(id = "tam", label = "Tamil"))
      val teiData = createTeiDataWith(
        languages = languages,
        nestedTeiData = Right(
          List(
            createTeiDataWith(languages = List()),
            createTeiDataWith(languages = List()),
            createTeiDataWith(languages = List()),
          ))
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
        nestedTeiData = Right(
          innerLanguages.map { languages =>
            createTeiDataWith(languages = languages)
          }
        )
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
}
