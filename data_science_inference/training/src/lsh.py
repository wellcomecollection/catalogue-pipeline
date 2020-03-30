import numpy as np
from sklearn.cluster import KMeans


def split_features(feature_vectors, n_groups):
    feature_groups = np.split(
        feature_vectors,
        indices_or_sections=n_groups,
        axis=1
    )
    return feature_groups


def train_clusters(feature_group, m):
    clustering_alg = KMeans(n_clusters=m).fit(feature_group)
    return clustering_alg
