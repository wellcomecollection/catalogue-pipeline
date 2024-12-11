FROM public.ecr.aws/amazoncorretto/amazoncorretto:11 AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

RUN yum install awscli -y
COPY bash_secrets_extension.sh /opt/extensions/bash_secrets_extension.sh

ADD target/universal/stage /opt/docker
ENV CLASSPATH="/opt/docker/lib/*"
WORKDIR /opt/docker

FROM base AS ecs

ENTRYPOINT ["/opt/docker/bin/main"]

FROM base AS lambda_rie

ADD https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/download/v1.22/aws-lambda-rie /aws-lambda/aws-lambda-rie
RUN chmod +x /aws-lambda/aws-lambda-rie
ENTRYPOINT ["/aws-lambda/aws-lambda-rie"]

ENV use_downstream=stdio

# These are test values, for demonstration purposes only
ENV es_port=9200
ENV es_host=secret:elasticsearch/pipeline_storage_2024-11-18/private_host

CMD [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda", "weco.pipeline.relation_embedder.LambdaMain::handleRequest"]

FROM base

ENTRYPOINT [ "/usr/bin/java", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD ["weco.pipeline.relation_embedder.LambdaMain::handleRequest"]

