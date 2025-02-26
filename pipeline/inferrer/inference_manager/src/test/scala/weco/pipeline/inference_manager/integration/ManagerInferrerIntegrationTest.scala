package weco.pipeline.inference_manager.integration

import scala.concurrent.duration._
import scala.io.Source
import scala.collection.mutable
import java.io.File
import java.nio.file.Paths
import org.apache.pekko.http.scaladsl.Http
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, Inspectors, OptionValues}
import software.amazon.awssdk.services.sqs.model.Message
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.catalogue.internal_model.image.ImageState.{Augmented, Initial}
import org.apache.pekko.http.scaladsl.model.Uri
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, InferredData}
import weco.catalogue.internal_model.locations.LocationType
import weco.pipeline.inference_manager.adapters.{
  AspectRatioInferrerAdapter,
  FeatureVectorInferrerAdapter,
  InferrerAdapter,
  PaletteInferrerAdapter
}
import weco.pipeline.inference_manager.fixtures.InferenceManagerWorkerServiceFixture
import weco.pipeline.inference_manager.models.DownloadedImage
import weco.pipeline.inference_manager.services.{
  DefaultFileWriter,
  MergedIdentifiedImage
}

class ManagerInferrerIntegrationTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with OptionValues
    with Inside
    with Inspectors
    with Eventually
    with IntegrationPatience
    with InferenceManagerWorkerServiceFixture {

  it("augments images with features, palettes, and aspect ratios") {
    val image = createImageDataWith(
      locations = List(
        createDigitalLocationWith(
          locationType = LocationType.IIIFImageAPI,
          url = s"http://localhost:$localImageServerPort/test-image.jpg"
        )
      )
    ).toInitialImage

    withWorkerServiceFixtures(image) {
      case (QueuePair(queue, dlq), messageSender, augmentedImages, rootDir) =>
        // This is (more than) enough time for the inferrer to have
        // done its prestart work and be ready to use
        eventually(Timeout(scaled(90 seconds))) {
          inferrersAreHealthy shouldBe true
        }

        sendNotificationToSQS(queue = queue, body = image.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          rootDir.listFiles().length should be(0)

          val augmentedImage =
            augmentedImages(messageSender.messages.head.body)

          inside(augmentedImage.state) {
            case Augmented(_, id, inferredData) =>
              id should be(image.state.canonicalId)
              inside(inferredData) {
                case InferredData(
                      features,
                      paletteEmbedding,
                      Some(averageColorHex),
                      aspectRatio
                    ) =>
                  // Note for future explorers: if a vector is the wrong length,
                  // make sure that the inferrer is encoding a list of single precision
                  // floats (ie float32/double) as that's what we're expecting to decode!
                  features should have length 4096
                  forAll(features) { _.isNaN shouldBe false }
                  paletteEmbedding should have length 1000
                  averageColorHex should have length 7
                  aspectRatio should not be empty
              }
          }
        }
    }
  }

  val localInferrerPorts: Map[String, Int] = Map(
    "feature" -> 3141,
    "palette" -> 3142,
    "aspect_ratio" -> 3143
  )
  val localImageServerPort = 2718

  def inferrersAreHealthy: Boolean =
    localInferrerPorts.values.forall {
      port =>
        val source =
          Source.fromURL(s"http://localhost:$port/healthcheck")
        try source.mkString.nonEmpty
        catch {
          case _: Exception => false
        } finally source.close()
    }

  def withWorkerServiceFixtures[R](image: Image[Initial])(
    testWith: TestWith[
      (
        QueuePair,
        MemoryMessageSender,
        mutable.Map[String, Image[Augmented]],
        File
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair() {
      queuePair =>
        val messageSender = new MemoryMessageSender()
        val root = Paths.get("integration-tmp").toFile
        root.mkdir()
        withActorSystem {
          implicit actorSystem =>
            val augmentedImages = mutable.Map.empty[String, Image[Augmented]]
            withWorkerService(
              queuePair.queue,
              messageSender,
              augmentedImages = augmentedImages,
              initialImages = List(image),
              adapters = Set(
                new FeatureVectorInferrerAdapter(
                  "localhost",
                  localInferrerPorts("feature")
                ),
                new PaletteInferrerAdapter(
                  "localhost",
                  localInferrerPorts("palette")
                ),
                new AspectRatioInferrerAdapter(
                  "localhost",
                  localInferrerPorts("aspect_ratio")
                )
              ),
              fileWriter = new DefaultFileWriter(root.getPath),
              inferrerRequestPool = Http()
                .superPool[((DownloadedImage, InferrerAdapter), Message)](),
              imageRequestPool =
                Http().superPool[((Uri, MergedIdentifiedImage), Message)](),
              fileRoot = root.getPath
            ) {
              _ =>
                try {
                  testWith((queuePair, messageSender, augmentedImages, root))
                } finally {
                  root.delete()
                }
            }
        }
    }
}
