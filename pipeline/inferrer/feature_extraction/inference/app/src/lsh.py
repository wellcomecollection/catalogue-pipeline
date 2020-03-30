import os
import pickle

import numpy as np


class LSHEncoder():
    def __init__(self):
        model_path = os.path.basename(os.environ['MODEL_OBJECT_KEY'])
        with open(model_path, 'rb') as f:
            self.models = pickle.load(f)

    def encode_for_elasticsearch(self, clusters):
        return [f'{i}-{val}' for i, val in enumerate(clusters)]

    def __call__(self, feature_vector):
        feature_groups = np.split(feature_vector, len(self.models))

        clusters = [
            model.predict(feature_group.reshape(1, -1))[0]
            for model, feature_group in zip(self.models, feature_groups)
        ]

        return self.encode_for_elasticsearch(clusters)
