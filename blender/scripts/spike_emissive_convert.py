# Post-bake: Blender RGB emissive -> RGBA PNG (RGB = white, A = max(R,G,B)). ADR section 1. System python + Pillow.
import sys
from PIL import Image
import numpy as np
src, dst = sys.argv[1], sys.argv[2]
a = np.asarray(Image.open(src).convert("RGB")).astype(np.uint8)
out = np.empty(a.shape[:2] + (4,), dtype=np.uint8)
out[..., :3] = 255
out[..., 3] = a.max(axis=2)
Image.fromarray(out, "RGBA").save(dst)
print(dst, out.shape, "opaque texels:", int((out[..., 3] > 0).sum()))
