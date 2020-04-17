import os
import pickle

import numpy as np


class LSHEncoder:
    def __init__(self):
        model_path = os.path.join(
            os.path.dirname(__file__),
            "../data",
            os.path.basename(os.environ["MODEL_OBJECT_KEY"])
        )
        with open(model_path, "rb") as f:
            self.models = pickle.load(f)

    @staticmethod
    def encode_for_elasticsearch(clusters):
        return [f"{i}-{val}" for i, val in enumerate(clusters)]

    def __call__(self, feature_vectors):
        feature_groups = np.split(feature_vectors, len(self.models), axis=1)

        clusters = np.stack(
            [
                model.predict(feature_group)
                for model, feature_group in zip(self.models, feature_groups)
            ],
            axis=1,
        )

        return [LSHEncoder.encode_for_elasticsearch(c) for c in clusters]
