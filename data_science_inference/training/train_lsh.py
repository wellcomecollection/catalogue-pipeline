import pickle

from datetime import datetime
import click
import numpy as np

from src.aws import put_object_to_s3
from src.lsh import split_features, train_clusters
from src.elastic import get_random_feature_vectors


@click.command()
@click.option('-n', help='number of groups to split the feature vectors into', default=256)
@click.option('-m', help='number of clusters to find within each feature group', default=32)
@click.option('--sample_size', help='number of embeddings to train clusters on', default=25_000)
def main(n, m, sample_size):
    feature_vectors = get_random_feature_vectors(sample_size)

    model_list = [
        train_clusters(feature_group, m)
        for feature_group in split_features(feature_vectors, n)
    ]

    model_name = datetime.now().strftime('%Y-%m-%d')

    put_object_to_s3(
        binary_object=pickle.dumps(model_list),
        key=f'lsh_models/{model_name}.pkl',
        bucket_name='model-core-data',
        profile_name='data-dev'
    )


if __name__ == "__main__":
    main()
