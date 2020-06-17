import pickle
import numpy as np
import subprocess
from datetime import datetime
from sklearn.cluster import KMeans
from sklearn.externals.joblib import Parallel, delayed
from tqdm import tqdm
from .aws import get_ecs_container_metadata
from .parallel import tqdm_joblib


def split_features(feature_vectors, n_groups):
    feature_groups = np.split(
        feature_vectors, indices_or_sections=n_groups, axis=1)
    return feature_groups


def train_clusters(feature_group, m):
    clustering_alg = KMeans(n_clusters=m).fit(feature_group)
    return clustering_alg


def get_object_name():
    timestamp = datetime.now().isoformat()
    try:
        container_metadata = get_ecs_container_metadata()
        image = container_metadata["Image"]
        tag = image.split(":")[1]
    except Exception:
        try:
            tag = (
                subprocess.check_output(["git", "rev-parse", "HEAD"])
                .decode("ascii")
                .strip()
            )
        except Exception:
            raise Exception(
                "Could not fetch ECS image tag or find local git hash")

    return f"{tag}/{timestamp}"


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
        "name": get_object_name(),
        "prefix": "lsh_model",
    }
