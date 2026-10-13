#!/usr/bin/env python3
"""Export a SegFormer semantic-segmentation model to ONNX for the HearSight app.

The default model is SegFormer-B0 fine-tuned on Cityscapes (19 street-scene classes:
road, sidewalk, person, car, ...), about 3.7M parameters, small enough to run on a phone.
The app maps its classes to walking-relevant surfaces (navcore Semantics.kt).

Usage (on a laptop with internet access):
    python -m venv .venv && . .venv/bin/activate
    pip install torch transformers onnx onnxruntime onnxscript
    python scripts/export_segmentation_model.py

This writes app/src/main/assets/segmentation.onnx. Rebuild the app to enable segmentation.

--random-weights builds the same architecture without downloading anything (for testing
the export and the app's input/output handling; its predictions are meaningless).
"""
import argparse
import pathlib
import sys

import numpy as np
import torch
from transformers import SegformerConfig, SegformerForSemanticSegmentation

DEFAULT_MODEL = "nvidia/segformer-b0-finetuned-cityscapes-512-1024"
ROOT = pathlib.Path(__file__).resolve().parent.parent


class LogitsOnly(torch.nn.Module):
    """Wraps the Hugging Face model so the ONNX graph has one input and one output."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, pixel_values):
        return self.model(pixel_values=pixel_values).logits


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=DEFAULT_MODEL, help="Hugging Face model id")
    ap.add_argument("--width", type=int, default=448, help="input width (multiple of 32; D455 depth is 16:9)")
    ap.add_argument("--height", type=int, default=256, help="input height (multiple of 32)")
    ap.add_argument("--out", default=str(ROOT / "app/src/main/assets/segmentation.onnx"))
    ap.add_argument("--random-weights", action="store_true", help="SegFormer-B0 architecture, untrained")
    args = ap.parse_args()

    if args.width % 32 or args.height % 32:
        sys.exit("--width and --height must be multiples of 32")

    if args.random_weights:
        config = SegformerConfig(num_labels=19)  # defaults are the B0 architecture
        model = SegformerForSemanticSegmentation(config)
    else:
        model = SegformerForSemanticSegmentation.from_pretrained(args.model)
    model.eval()
    labels = [model.config.id2label[i] for i in range(model.config.num_labels)] if not args.random_weights else []
    if labels:
        print("classes:", ", ".join(labels))
        if len(labels) != 19:
            print("warning: the app's class mapping (Cityscapes.TO_SURFACE) expects the 19 Cityscapes classes")

    # eval() on the wrapper too: export restores the wrapper's mode afterwards, and a wrapper
    # left in training mode would switch dropout back on in the model.
    wrapped = LogitsOnly(model).eval()
    dummy = torch.randn(1, 3, args.height, args.width)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)

    export_kwargs = dict(
        input_names=["pixel_values"],
        output_names=["logits"],
        opset_version=17,
        do_constant_folding=True,
    )
    try:
        torch.onnx.export(wrapped, (dummy,), str(out), dynamo=False, **export_kwargs)
    except TypeError:  # older torch without the dynamo argument
        torch.onnx.export(wrapped, (dummy,), str(out), **export_kwargs)

    # Check the ONNX model against PyTorch.
    import onnxruntime as ort

    wrapped.eval()
    with torch.no_grad():
        expected = wrapped(dummy).numpy()
    session = ort.InferenceSession(str(out), providers=["CPUExecutionProvider"])
    got = session.run(None, {"pixel_values": dummy.numpy()})[0]
    diff = float(np.abs(expected - got).max())
    print(f"input  pixel_values {list(dummy.shape)}")
    print(f"output logits       {list(got.shape)}  (classes, height/4, width/4)")
    print(f"max |onnx - torch| = {diff:.2e}")
    if diff > 1e-3:
        sys.exit("ONNX output doesn't match PyTorch")
    print(f"wrote {out} ({out.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
