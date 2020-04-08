import pickle
import numpy as np
from datetime import datetime
from sklearn.cluster import KMeans
from sklearn.externals.joblib import Parallel, delayed
from tqdm import tqdm
from .parallel import tqdm_joblib


def split_features(feature_vectors, n_groups):
    feature_groups = np.split(feature_vectors, indices_or_sections=n_groups, axis=1)
    return feature_groups


def train_clusters(feature_group, m):
    clustering_alg = KMeans(n_clusters=m).fit(feature_group)
    return clustering_alg


def get_object_for_storage(feature_vectors, m, n, verbose=False):
    print("Fitting clusters...")
    feature_groups = split_features(feature_vectors, n)

    with tqdm_joblib(tqdm(total=n, disable=(not verbose), unit="cluster")):
        model_list = Parallel(n_jobs=-1)(
            delayed(train_clusters)(feature_group, m)
            for feature_group in feature_groups
        )

    return {
        "object_binary": pickle.dumps(model_list),
        "name": datetime.now().strftime("%Y-%m-%d"),
        "prefix": "lsh_model",
    }
