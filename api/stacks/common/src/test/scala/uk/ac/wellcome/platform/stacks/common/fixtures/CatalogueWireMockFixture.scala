package uk.ac.wellcome.platform.stacks.common.fixtures

import java.io.File
import java.nio.file.{Files, Path, Paths}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import grizzled.slf4j.Logging
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.Outcome
import uk.ac.wellcome.fixtures.TestWith

/** This is a Wiremock fixture for interacting with the Catalogue API.
  *
  * Because the Catalogue API is publicly available and does not require auth,
  * it will automatically record any API interactions, then save them to the
  * fixtures folder and replay them in future tests.
  *
  * It is inspired by libraries like:
  *   - vcr (https://rubygems.org/gems/vcr)
  *   - Betamax (https://pypi.org/project/betamax/)
  *
  */
trait CatalogueWireMockFixture extends AnyFunSpec with Logging {
  val baseRecordingRoot: String =
    "./api/stacks/common/src/test/resources/catalogue"
  var recordingRoot: Path = Paths.get(baseRecordingRoot)

  // Each test has a separate set of recordings, in a separate directory,
  // so we can see which recording is used by which test.
  //
  // This overrides withFixture() to capture the name of the test, and then
  // turns it into a filesystem-safe name for a directory to keep the
  // recordings in.
  //
  // e.g. recordings for `it("does the right thing")` go to `does-the-right-thing`
  override def withFixture(test: NoArgTest): Outcome = {
    val dirname = test.name
      .replaceAll("[^A-Za-z0-9-]", "-")
      .toLowerCase

    recordingRoot = Paths.get(baseRecordingRoot, dirname)
    test()
  }

  def withMockCatalogueServer[R](
    testWith: TestWith[String, R]
  ): R = {
    val wireMockServer = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .withRootDirectory(recordingRoot.toString)
        .dynamicPort()
    )

    wireMockServer.start()

    // Are there any existing recordings for this test?  If so, we should
    // configure Wiremock to get new recordings.
    val shouldRecord: Boolean = !existingRecordingsIn(recordingRoot)

    if (shouldRecord) {
      info("Recording new Wiremock fixtures for the catalogue API")

      new File(recordingRoot.toString).mkdir()
      new File(s"$recordingRoot/mappings").mkdir()

      wireMockServer.startRecording("https://api.wellcomecollection.org")
    } else {
      info("Using cached Wiremock fixtures")
    }

    val result = testWith(s"http://localhost:${wireMockServer.port()}")

    if (shouldRecord) {
      wireMockServer.saveMappings()
      wireMockServer.stopRecording()
    }

    wireMockServer.shutdown()

    result
  }

  private def existingRecordingsIn(p: Path): Boolean = {
    val mappingsPath = Paths.get(p.toString, "mappings")

    isNonEmpty(mappingsPath)
  }

  private def exists(p: Path): Boolean =
    Files.exists(p)

  private def isNonEmpty(dirpath: Path): Boolean =
    exists(dirpath) && Files.list(dirpath).findAny().isPresent
}
