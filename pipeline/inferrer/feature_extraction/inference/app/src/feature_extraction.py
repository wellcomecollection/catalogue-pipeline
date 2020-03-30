import torch
from torchvision import transforms
from torchvision.models.vgg import vgg16

device = (
    torch.device('cuda')
    if torch.cuda.is_available()
    else torch.device('cpu')
)

imagenet_mean = [0.485, 0.456, 0.406]
imagenet_std = [0.229, 0.224, 0.225]

transform_pipeline = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=imagenet_mean, std=imagenet_std)
])

feature_extractor = vgg16(pretrained=True, progress=False).to(device).eval()
feature_extractor.classifier = feature_extractor.classifier[:4]


def extract_features(image):
    image_tensor = transform_pipeline(image)
    features = feature_extractor(image_tensor.unsqueeze(0))
    return features.squeeze().detach().cpu().numpy()
