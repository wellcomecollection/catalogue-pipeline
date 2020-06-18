import pickle
import numpy as np
import subprocess
import os
import sys
from datetime import datetime
from sklearn.cluster import KMeans
from tqdm import tqdm
from .aws import get_ecs_container_metadata
from .logging import get_logger

logger = get_logger(__name__)


def split_features(feature_vectors, n_groups):
    feature_groups = np.split(feature_vectors, indices_or_sections=n_groups, axis=1)
    return feature_groups


def train_clusters(feature_group, m):
    clustering_alg = KMeans(n_clusters=m, n_jobs=-1).fit(feature_group)
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
            raise Exception("Could not fetch ECS image tag or find local git hash")

    return f"{tag}/{timestamp}"


def get_object_for_storage(feature_vectors, m, n, tty=True):
    logger.info("Fitting clusters...")
    feature_groups = split_features(feature_vectors, n)

    model_list = []
    tqdm_output = sys.stdout if tty else open(os.devnull, "w")
    with tqdm(feature_groups, file=tqdm_output) as progress:
        for feature_group in progress:
            if not tty:
                logger.info(repr(progress))
            model_list.append(train_clusters(feature_group, m))

    logger.info("Fitted clusters.")

    return {
        "object_binary": pickle.dumps(model_list),
        "name": get_object_name(),
        "prefix": "lsh_model",
    }
