package uk.ac.wellcome.platform.ingestor.works.services

import com.sksamuel.elastic4s.ElasticDsl.properties
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import com.sksamuel.elastic4s.requests.mappings.{ObjectField, TextField}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork

import scala.concurrent.ExecutionContext.Implicits.global

class WorkIndexerTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with ElasticsearchFixtures
    with WorksGenerators {

  val workIndexer = new WorkIndexer(client = elasticClient)

  describe("updating merged / redirected works") {
    it(
      "doesn't override a merged Work with same version but merged flag = false") {
      val mergedWork = createIdentifiedWorkWith(version = 3, merged = true)
      val unmergedWork = mergedWork.withData(_.copy(merged = false))

      withLocalWorksIndex { index =>
        val unmergedWorkInsertFuture = ingestWorkPairInOrder(
          firstWork = mergedWork,
          secondWork = unmergedWork,
          index = index
        )

        whenReady(unmergedWorkInsertFuture) { result =>
          assertIngestedWorkIs(
            result = result,
            ingestedWork = mergedWork,
            index = index)
        }
      }
    }

    it("doesn't overwrite a Work with lower version and merged = true") {
      val unmergedNewWork = createIdentifiedWorkWith(version = 4)
      val mergedOldWork = unmergedNewWork
        .copy(version = 3)
        .withData(_.copy(merged = true))

      withLocalWorksIndex { index =>
        val mergedWorkInsertFuture = ingestWorkPairInOrder(
          firstWork = unmergedNewWork,
          secondWork = mergedOldWork,
          index = index
        )
        whenReady(mergedWorkInsertFuture) { result =>
          assertIngestedWorkIs(
            result = result,
            ingestedWork = unmergedNewWork,
            index = index)
        }
      }
    }

    it(
      "doesn't override a identified Work with redirected work with lower version") {
      val identifiedNewWork = createIdentifiedWorkWith(version = 4)
      val redirectedOldWork = createIdentifiedRedirectedWorkWith(
        canonicalId = identifiedNewWork.canonicalId,
        version = 3)

      withLocalWorksIndex { index =>
        val redirectedWorkInsertFuture = ingestWorkPairInOrder(
          firstWork = identifiedNewWork,
          secondWork = redirectedOldWork,
          index = index
        )
        whenReady(redirectedWorkInsertFuture) { result =>
          assertIngestedWorkIs(
            result = result,
            ingestedWork = identifiedNewWork,
            index = index)
        }
      }
    }

    it("doesn't override a redirected Work with identified work same version") {
      val redirectedWork = createIdentifiedRedirectedWorkWith(version = 3)
      val identifiedWork = createIdentifiedWorkWith(
        canonicalId = redirectedWork.canonicalId,
        version = 3)

      withLocalWorksIndex { index =>
        val identifiedWorkInsertFuture = ingestWorkPairInOrder(
          firstWork = redirectedWork,
          secondWork = identifiedWork,
          index = index
        )
        whenReady(identifiedWorkInsertFuture) { result =>
          assertIngestedWorkIs(
            result = result,
            ingestedWork = redirectedWork,
            index = index)
        }
      }
    }

    it("overrides a identified Work with invisible work with higher version") {
      val work = createIdentifiedWorkWith(version = 3)
      val invisibleWork = createIdentifiedInvisibleWorkWith(
        canonicalId = work.canonicalId,
        version = 4)

      withLocalWorksIndex { index =>
        val invisibleWorkInsertFuture = ingestWorkPairInOrder(
          firstWork = work,
          secondWork = invisibleWork,
          index = index
        )
        whenReady(invisibleWorkInsertFuture) { result =>
          assertIngestedWorkIs(
            result = result,
            ingestedWork = invisibleWork,
            index = index)
        }
      }
    }
  }

  object WorksWithNoEditionIndexConfig extends IndexConfig {
    import uk.ac.wellcome.elasticsearch.WorksIndexConfig.{fields, analysis => defaultAnalysis}

    val fieldsWithNoEdition = fields.map {
      case data: ObjectField if data.name == "data" =>
        data.copy(fields = data.fields.filter {
          case edition: TextField if edition.name == "edition" => false
          case _                                               => true
        })
      case field => field
    }

    val analysis = defaultAnalysis
    val mapping = properties(fieldsWithNoEdition).dynamic(DynamicMapping.Strict)
  }

  it("returns a list of Works that weren't indexed correctly") {
    val validWorks = createIdentifiedInvisibleWorks(count = 5)
    val notMatchingMappingWork = createIdentifiedWorkWith(
      edition = Some("An edition")
    )

    val works = validWorks :+ notMatchingMappingWork

    withLocalElasticsearchIndex(config = WorksWithNoEditionIndexConfig) {
      index =>
        val future = workIndexer.index(
          documents = works,
          index = index
        )

        whenReady(future) { result =>
          assertElasticsearchEventuallyHasWork(index = index, validWorks: _*)
          assertElasticsearchNeverHasWork(index = index, notMatchingMappingWork)
          result.left.get should contain only (notMatchingMappingWork)
        }
    }
  }

  private def ingestWorkPairInOrder(firstWork: IdentifiedBaseWork,
                                    secondWork: IdentifiedBaseWork,
                                    index: Index) =
    for {
      _ <- indexWork(firstWork, index)
      result <- indexWork(secondWork, index)
    } yield result

  private def indexWork(work: IdentifiedBaseWork, index: Index) =
    workIndexer.index(
      documents = List(work),
      index = index
    )

  private def assertIngestedWorkIs(
    result: Either[Seq[IdentifiedBaseWork], Seq[IdentifiedBaseWork]],
    ingestedWork: IdentifiedBaseWork,
    index: Index): Seq[Assertion] = {
    result.isRight shouldBe true
    assertElasticsearchEventuallyHasWork(index = index, ingestedWork)
  }
}
