from joblib import Parallel, delayed


class Encoder:
    def __init__(self):
        """
        Instantiate an encoder which determines the aspect ratio of a given
        image, where the aspect ratio (R) = width / height. By definition, R
        should always be positive.

        0 < R < 1 : portrait
        r = 1 : square
        R > 1 : landscape
        """
        self.delayed_process_image = delayed(self.process_image)

    def process_image(self, image):
        return image.width / image.height

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
