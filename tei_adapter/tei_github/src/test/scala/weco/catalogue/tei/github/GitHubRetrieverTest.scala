package weco.catalogue.tei.github

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import java.time.ZonedDateTime


class GitHubRetrieverTest extends AnyFunSpec with Matchers{
  it("retrieves files modified in a time window"){
    val retriever = GitHubRetriever("http://localhost:8080")
    retriever.getFiles(Window( ZonedDateTime.parse("2021-05-05T10:00:00Z"), ZonedDateTime.parse("2021-05-07T18:01:00Z"))) should contain theSameElementsAs List(
      "https://github.com/wellcomecollection/wellcome-collection-tei/raw/1e394d3186b6a8ed5f0fa8af33b99bdc59d7c544/Arabic/WMS_Arabic_49.xml",
      "https://github.com/wellcomecollection/wellcome-collection-tei/raw/481fa2d65ec2b44a4293823aa86b6f5e455a7c0d/Arabic/WMS_Arabic_46.xml",
      "https://github.com/wellcomecollection/wellcome-collection-tei/raw/db7581026bb9149330225dc2b9411202b3cd6894/Arabic/WMS_Arabic_62.xml"
    )

  }
}
