# The harness on top of the CI-built unified Lambda image. No RUN steps, so the
# amd64 image builds on an arm64 laptop without emulating Python.
ARG BASE_TAG=latest
FROM 760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/unified_pipeline_lambda:${BASE_TAG}

COPY catalogue_graph/src/es_bench ${LAMBDA_TASK_ROOT}/es_bench

CMD [ "es_bench.handler.lambda_handler" ]
