import torch
from torch.utils import mkldnn as mkldnn_utils
from torchvision import transforms
from torchvision.models.vgg import vgg16

use_mkldnn = True
use_gpu = torch.cuda.is_available()
device = torch.device("cuda" if use_gpu else "cpu")

imagenet_mean = [0.485, 0.456, 0.406]
imagenet_std = [0.229, 0.224, 0.225]

transform_pipeline = transforms.Compose(
    [
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=imagenet_mean, std=imagenet_std),
    ]
)

feature_extractor = vgg16(pretrained=True, progress=False).to(device).eval()
feature_extractor.classifier = feature_extractor.classifier[:4]
if use_mkldnn and not use_gpu:
    feature_extractor = mkldnn_utils.to_mkldnn(feature_extractor)


def extract_features(images):
    image_tensors = [transform_pipeline(image) for image in images]
    image_stack = torch.stack(image_tensors, 0)
    if use_mkldnn and not use_gpu:
        image_stack = image_stack.to_mkldnn()

    features_stack = feature_extractor(image_stack)
    if use_mkldnn and not use_gpu:
        features_stack = features_stack.to_dense()
    return features_stack.detach().cpu().numpy()
