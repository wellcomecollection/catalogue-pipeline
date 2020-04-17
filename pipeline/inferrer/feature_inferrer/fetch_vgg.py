from torch.hub import load_state_dict_from_url
from torchvision.models.vgg import model_urls

try:
    print("Fetching pretrained VGG16 model")
    # This will be cached in $TORCH_HOME/checkpoints
    load_state_dict_from_url(model_urls["vgg16"])
    print("Fetched pretrained VGG model")
except Exception as e:
    print(f"Failed to fetch pretrained VGG model: {e}")
    raise
