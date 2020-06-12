import click

from src.elastic import get_random_feature_vectors
from src.lsh import get_object_for_storage
from src.storage import store_model

# The number of groups to split the feature vectors into
N_GROUPS = 256
# The number of clusters to find within each feature group
N_CLUSTERS = 256

@click.command()
@click.option(
    "--sample_size", help="number of embeddings to train clusters on", default=25000
)
@click.option(
    "--bucket-name",
    help="Name of the S3 bucket in which model data is stored",
    envvar="MODEL_DATA_BUCKET",
)
@click.option(
    "--ssm-path",
    help="The path of the SSM parameter in which to store the model key",
    envvar="MODEL_SSM_PATH",
)
def main(sample_size, bucket_name, ssm_path):
    feature_vectors = get_random_feature_vectors(sample_size)

    model = get_object_for_storage(feature_vectors, N_CLUSTERS, N_GROUPS)
    store_model(bucket_name=bucket_name, ssm_path=ssm_path, **model)


if __name__ == "__main__":
    main()
