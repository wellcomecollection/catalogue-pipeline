package uk.ac.wellcome.platform.merger.rules
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifiableRedirect,
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.MergedWork

class MergerRuleTest extends FunSpec with WorksGenerators with Matchers {
  it("merges a pair of works in a list") {
    val mergerRule = new MergerRule with Partitioner with WorkPairMerger {
      override protected def partitionWorks(
        works: Seq[BaseWork]): Option[Partition] =
        Some(
          Partition(
            PotentialMergedWork(
              works.head.asInstanceOf[UnidentifiedWork],
              works.tail.head.asInstanceOf[UnidentifiedWork]
            ),
            works.tail.tail
          )
        )
      override protected def mergeAndRedirectWorkPair(
        firstWork: UnidentifiedWork,
        secondWork: TransformedBaseWork): Option[MergedWork] =
        Some(
          MergedWork(
            firstWork,
            createUnidentifiedRedirectedWork(secondWork, firstWork)
          )
        )
    }
    val works = createUnidentifiedWorks(5)

    val expectedMergedWork =
      works.head.withData(data => data.copy(merged = true))
    val expectedRedirectedWork = UnidentifiedRedirectedWork(
      version = 1,
      sourceIdentifier = works.tail.head.sourceIdentifier,
      redirect = IdentifiableRedirect(works.head.sourceIdentifier))
    val expectedWorks = expectedMergedWork +: expectedRedirectedWork +: works.tail.tail
    mergerRule.mergeAndRedirectWorks(works) shouldBe expectedWorks
  }

  it("returns the works unchanged if the list cannot be partitioned") {
    val mergerRule = new MergerRule with Partitioner with WorkPairMerger {
      override protected def partitionWorks(
        works: Seq[BaseWork]): Option[Partition] = None
      override protected def mergeAndRedirectWorkPair(
        firstWork: UnidentifiedWork,
        secondWork: TransformedBaseWork): Option[MergedWork] = None
    }

    val works = createUnidentifiedWorks(5)
    mergerRule.mergeAndRedirectWorks(works) shouldBe works
  }

  it(
    "returns the works unchanged if the list can be partitioned but the work pair merger returns none") {
    val mergerRule = new MergerRule with Partitioner with WorkPairMerger {
      override protected def partitionWorks(
        works: Seq[BaseWork]): Option[Partition] =
        Some(
          Partition(
            PotentialMergedWork(
              works.head.asInstanceOf[UnidentifiedWork],
              works.tail.head.asInstanceOf[UnidentifiedWork]
            ),
            works.tail.tail
          )
        )
      override protected def mergeAndRedirectWorkPair(
        firstWork: UnidentifiedWork,
        secondWork: TransformedBaseWork): Option[MergedWork] = None
    }

    val works = createUnidentifiedWorks(5)
    mergerRule.mergeAndRedirectWorks(works) shouldBe works
  }

}
