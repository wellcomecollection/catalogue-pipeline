package uk.ac.wellcome.platform.transformer.miro.services

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.{
  MiroMetadata,
  MiroVHSRecord
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.DoesNotExistError
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryStore

class MiroLookupTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with MiroRecordGenerators
    with S3ObjectLocationGenerators {

  it("fetches a Miro record from the store") {
    val record = createMiroRecord
    val metadata =
      MiroMetadata(isClearedForCatalogueAPI = chooseFrom(true, false))
    val version = randomInt(from = 1, to = 10)

    val vhsReader = createVhsReader
    val typedStore = createTypedStore

    val lookup = new MiroLookup(vhsReader, typedStore)

    // Store the record in the store
    val id = record.imageNumber
    val s3Location = createS3ObjectLocation
    typedStore.put(s3Location)(record) shouldBe a[Right[_, _]]

    val vhsRecord = MiroVHSRecord(
      id = id,
      version = version,
      isClearedForCatalogueAPI = metadata.isClearedForCatalogueAPI,
      location = s3Location
    )
    vhsReader.put(id)(vhsRecord) shouldBe a[Right[_, _]]

    lookup.lookupRecord(id).value shouldBe ((record, metadata))
  }

  it("fails if asked to lookup a non-existent ID") {
    val vhsReader = createVhsReader
    val typedStore = createTypedStore

    val lookup = new MiroLookup(vhsReader, typedStore)

    lookup
      .lookupRecord(id = "M0000001")
      .left
      .value shouldBe a[DoesNotExistError]
  }

  it("fails if the index store has a dangling pointer") {
    val vhsReader = createVhsReader
    val typedStore = createTypedStore

    val id = "L1234567"

    val vhsRecord = MiroVHSRecord(
      id = id,
      version = randomInt(from = 1, to = 10),
      isClearedForCatalogueAPI = chooseFrom(true, false),
      location = createS3ObjectLocation
    )
    vhsReader.put(id)(vhsRecord) shouldBe a[Right[_, _]]

    val lookup = new MiroLookup(vhsReader, typedStore)

    lookup.lookupRecord(id).left.value shouldBe a[DoesNotExistError]
  }

  private def createVhsReader: MemoryStore[String, MiroVHSRecord] =
    new MemoryStore[String, MiroVHSRecord](initialEntries = Map.empty)

  private def createTypedStore: MemoryStore[S3ObjectLocation, MiroRecord] =
    new MemoryStore[S3ObjectLocation, MiroRecord](initialEntries = Map.empty)
}
