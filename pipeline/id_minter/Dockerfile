FROM public.ecr.aws/docker/library/eclipse-temurin:11-jre-alpine as base

LABEL maintainer = "Wellcome Collection <digital@wellcomecollection.org>"

RUN apk add --no-cache bash

ADD target/universal/stage /opt/docker

FROM base AS ecs
ENTRYPOINT ["/opt/docker/bin/id_minter"]

FROM base AS lambda_rie

ADD https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/download/v1.22/aws-lambda-rie /aws-lambda/aws-lambda-rie
RUN chmod +x /aws-lambda/aws-lambda-rie
ENTRYPOINT ["/aws-lambda/aws-lambda-rie"]

ENV use_downstream=stdio

CMD [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda", "weco.pipeline.id_minter.LambdaMain::handleRequest"]

FROM base as lambda_live

ENTRYPOINT [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD ["weco.pipeline.id_minter.LambdaMain::handleRequest"]

FROM ecs
