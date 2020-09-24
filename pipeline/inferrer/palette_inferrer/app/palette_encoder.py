from collections import Counter
import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed


class PaletteEncoder:
    def __init__(self, palette_weights, bin_sizes):
        """
        Instantiate a palette encoder that looks for len(palette_weights) colours,
        weighted by palette_weights in descending order of cluster cardinality,
        and quantises the resultant colors across bin_sizes bins, respectively.
        """
        self.palette_size = len(palette_weights)
        self.palette_weights = palette_weights
        self.bin_sizes = bin_sizes
        self.delayed_process_image = delayed(self.process_image)

    @staticmethod
    def get_significant_colours(image, n):
        """
        extract n significant colours from the image pixels by taking the
        centres of n kmeans clusters of the image's pixels arranged in colour
        space. Returns colours in descending order of cluster cardinality.
        """
        pixels = np.array(image).reshape(-1, 3)

        # Only cluster distinct colours but weight them by their frequency
        # As per https://arxiv.org/pdf/1101.0395.pdf
        distinct_colours, distinct_colour_freqs = np.unique(
            pixels, axis=0, return_counts=True
        )

        # Fewer distinct colours than clusters
        if len(distinct_colours) <= n:
            sort_indices = distinct_colour_freqs.argsort()[::-1]
            sorted_by_freq = distinct_colours[sort_indices]
            size_diff = n - len(distinct_colours)
            # pad with the most frequently occurring distinct colour
            return np.pad(
                sorted_by_freq,
                pad_width=((size_diff, 0), (0, 0)),
                mode="edge",
            )

        clusters = KMeans(n_clusters=n).fit(
            distinct_colours, sample_weight=distinct_colour_freqs
        )

        # Sort clusters by the sum of the weights they contain
        label_weights = Counter()
        for label, weight in zip(clusters.labels_, distinct_colour_freqs):
            label_weights[label] += weight
        labels_by_weight = [label for label, _ in label_weights.most_common()]

        # Unfortunately we need to make sure that the labels correspond to the centroids
        # because that behaviour is not guaranteed by sklearn.
        # https://github.com/scikit-learn/scikit-learn/blob/0fb307bf3/sklearn/cluster/_kmeans.py#L851
        centroid_labels = clusters.predict(clusters.cluster_centers_)
        consistently_indexed_centroids = clusters.cluster_centers_[
            np.argsort(centroid_labels)
        ]

        return consistently_indexed_centroids[labels_by_weight]

    @staticmethod
    def get_bin_index(colour, d):
        indices = np.floor(d * colour / 256).astype(int)
        return indices[0] + d * indices[1] + d * d * indices[2]

    @staticmethod
    def encode_for_elasticsearch(values):
        """
        turn a list of bin indices into a list of strings for elasticsearch.
        weighting is performed by repeating elements `weight` times
        """
        return [
            f"{index}/{n_bins}"
            for index, n_bins, weight in values
            for _ in range(weight)
        ]

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        colours = self.get_significant_colours(image, self.palette_size)
        combined_results = [
            (self.get_bin_index(colour, n), n, weight)
            for n in self.bin_sizes
            for colour, weight in zip(colours, self.palette_weights)
        ]

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
