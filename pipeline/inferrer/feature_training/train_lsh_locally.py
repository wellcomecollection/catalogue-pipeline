import os

import click
import numpy as np
from tqdm import tqdm

from src.lsh import get_object_for_storage
from src.storage import store_model


@click.command()
@click.option(
    "-n", help="number of groups to split the feature vectors into", default=256
)
@click.option(
    "-m", help="number of clusters to find within each feature group", default=256
)
@click.option(
    "--sample-size", help="number of embeddings to train clusters on", default=25000
)
@click.option(
    "--feature-vector-path", help="path to a synced local version of the fvs in s3"
)
@click.option(
    "--bucket-name",
    help="Name of the S3 bucket in which model data is stored",
    default="wellcomecollection-inferrer-model-core-data",
)
@click.option(
    "--ssm-path",
    help="The path of the SSM parameter in which to store the model key",
    default="/catalogue_pipeline/config/models/latest/lsh_model",
)
def main(n, m, sample_size, feature_vector_path, bucket_name, ssm_path):
    ids = np.random.choice(
        os.listdir(feature_vector_path), size=sample_size, replace=False
    )

    print("Loading feature vectors...")
    feature_vectors = []
    for id in tqdm(ids, unit="vec"):
        with open(os.path.join(feature_vector_path, id)) as f:
            feature_vectors.append(np.fromfile(f, dtype=np.float32))

    feature_vectors = np.stack(feature_vectors)

    model = get_object_for_storage(feature_vectors, m, n, verbose=True)
    store_model(bucket_name=bucket_name, ssm_path=ssm_path, **model)


if __name__ == "__main__":
    main()
