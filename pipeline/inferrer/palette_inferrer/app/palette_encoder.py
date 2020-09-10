import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed


class PaletteEncoder:
    def __init__(self, palette_size, precision_levels):
        self.palette_size = palette_size
        self.precision_levels = precision_levels
        self.delayed_process_image = delayed(self.process_image)

    @staticmethod
    def get_significant_colours(image, n, p=0.5):
        """
        extract n significant colours from the image pixels by taking the
        centres of n kmeans clusters of the image's pixels arranged in colour
        space. Returns tuples of (colour, weight) where the weights are integers.
        The biggest cluster is weighted at n^p, the second largest (n-1)^p
        times, ..., and the smallest has weight 1.
        """
        pixels = np.array(image).reshape(-1, 3)
        clusters = KMeans(n_clusters=n).fit(pixels)
        cluster_labels, cluster_cardinalities = np.unique(
            clusters.labels_, return_counts=True
        )
        labels_by_size = cluster_labels[np.argsort(cluster_cardinalities)]

        # Unfortunately we need to make sure that the labels correspond to the centroids
        # because that behaviour is not guaranteed by sklearn.
        # https://github.com/scikit-learn/scikit-learn/blob/0fb307bf3/sklearn/cluster/_kmeans.py#L851
        centroid_labels = clusters.predict(clusters.cluster_centers_)
        consistently_indexed_centroids = clusters.cluster_centers_[
            np.argsort(centroid_labels)
        ]
        sorted_centroids = consistently_indexed_centroids[labels_by_size]

        weights = [int((i + 1) ** p) for i in range(n)]
        return list(zip(sorted_centroids, weights))

    def encode_for_elasticsearch(self, flat_values):
        """
        turn a list of bin counts into a list of strings for elasticsearch
        """
        encoded_list = []
        for index, value in enumerate(flat_values):
            encoded_list.extend([str(index)] * int(value))
        return encoded_list

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        colours = self.get_significant_colours(image)
        combined_results = np.array(
            [
                bin_count
                for precision in self.precision_levels
                for bin_count in self.get_colour_histogram(
                    colours=colours, precision=precision
                )
            ]
        )

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
