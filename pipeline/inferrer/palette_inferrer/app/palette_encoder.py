import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed


class PaletteEncoder:
    def __init__(self, palette_size, bin_sizes):
        self.palette_size = palette_size
        self.bin_sizes = bin_sizes
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

    @staticmethod
    def get_bin_index(colour, n_bins):
        indices = np.floor(n_bins * colour / 256).astype(int)
        d = n_bins - 1
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
            for colour, weight in colours
        ]

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
