import os
from torch.hub import load_state_dict_from_url
from torchvision.models import VGG16_Weights

try:
    print("Fetching pretrained VGG16 model")
    # This will be cached in $TORCH_HOME/checkpoints
    load_state_dict_from_url(
        VGG16_Weights.DEFAULT.url, progress=os.getenv("VERBOSE", default=False)
    )
    print("Fetched pretrained VGG model")
except Exception as e:
    print(f"Failed to fetch pretrained VGG model: {e}")
    raise
