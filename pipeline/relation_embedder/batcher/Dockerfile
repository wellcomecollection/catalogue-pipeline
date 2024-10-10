FROM public.ecr.aws/amazoncorretto/amazoncorretto:11 as base

LABEL maintainer = "Wellcome Collection <digital@wellcomecollection.org>"

ADD target/universal/stage /opt/docker

WORKDIR /opt/docker

FROM base as lambda

ENTRYPOINT [ "/usr/bin/java", "-cp", "./lib/*", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD ["weco.pipeline.batcher.LambdaMain::handleRequest"]

FROM base

ENTRYPOINT ["/opt/docker/bin/batcher"]
