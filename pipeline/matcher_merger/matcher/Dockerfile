FROM public.ecr.aws/l7a1d1z4/jvm_container_lambda:v1 AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

ADD target/universal/stage /opt/docker
ENV CLASSPATH="/opt/docker/lib/*"
WORKDIR /opt/docker

FROM base AS ecs

ENTRYPOINT ["/opt/docker/bin/matcher"]

FROM base AS lambda_rie

ADD https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/download/v1.22/aws-lambda-rie /aws-lambda/aws-lambda-rie
RUN chmod +x /aws-lambda/aws-lambda-rie
ENTRYPOINT ["/aws-lambda/aws-lambda-rie"]

ENV use_downstream=stdio

CMD [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda", "weco.pipeline.matcher.LambdaMain::handleRequest"]

FROM base

ENTRYPOINT [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD ["weco.pipeline.matcher.LambdaMain::handleRequest"]

