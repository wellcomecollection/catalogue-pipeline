FROM wellcome/typesafe_config_base:experiment1330290719

ADD target/universal/stage /opt/docker

RUN export messages_bucket_name=wellcomecollection-catalogue-20190723-messages
RUN export metrics_namespace=sierra_transformer
RUN export sns_arn=arn:aws:sns:eu-west-1:760097843905:catalogue-20190723_sierra_transformer
RUN export transformer_queue_id=https://sqs.eu-west-1.amazonaws.com/760097843905/catalogue_20190723_sierra_transformer

ENV PROJECT transformer_sierra
