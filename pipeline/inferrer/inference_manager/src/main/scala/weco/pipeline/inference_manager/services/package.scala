package weco.pipeline.inference_manager

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.scaladsl.{Flow, FlowWithContext}
import weco.catalogue.internal_model.image.{Image, ImageState}

import scala.util.Try

package object services {
  type MergedIdentifiedImage = Image[ImageState.Initial]

  // Because request pool flows cannot be FlowWithContexts, we have to manually
  // attach both the "usual" context object (the RequestCtx) and the context from
  // the FlowWithContext (the FlowCtx).
  //
  // See the docs for information on the purpose of this "context" element
  // https://doc.akka.io/docs/akka-http/current/client-side/host-level.html#using-a-host-connection-pool
  type RequestPoolFlow[RequestCtx, FlowCtx] =
    Flow[
      (HttpRequest, (RequestCtx, FlowCtx)),
      (Try[HttpResponse], (RequestCtx, FlowCtx)),
      _
    ]

  // This is a helper to make a RequestPoolFlow behave as a FlowWithContext
  // using the space we made for FlowCtx in the type above.
  implicit class RequestPoolFlowOps[RequestCtx, FlowCtx](
    requestPool: RequestPoolFlow[RequestCtx, FlowCtx]
  ) {
    type Input = (HttpRequest, RequestCtx)
    type IntermediateInput = (HttpRequest, (RequestCtx, FlowCtx))
    type Output = (Try[HttpResponse], RequestCtx)
    type IntermediateOutput = (Try[HttpResponse], (RequestCtx, FlowCtx))

    def asContextFlow: FlowWithContext[Input, FlowCtx, Output, FlowCtx, _] =
      requestPool
        .asFlowWithContext[Input, FlowCtx, FlowCtx](collapseRequestContext)(
          extractFlowContext
        )
        .map[(Try[HttpResponse], RequestCtx)] {
          case (triedResponse, (requestCtx, _)) =>
            (triedResponse, requestCtx)
        }

    private def collapseRequestContext(
      in: Input,
      flowCtx: FlowCtx
    ): IntermediateInput =
      in match {
        case (req, requestCtx) => (req, (requestCtx, flowCtx))
      }

    private def extractFlowContext(
      requestPoolOut: IntermediateOutput
    ): FlowCtx =
      requestPoolOut match {
        case (_, (_, extractableFlowCtx)) => extractableFlowCtx
      }
  }
}
