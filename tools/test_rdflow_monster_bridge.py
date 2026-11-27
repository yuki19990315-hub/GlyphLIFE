"""RD Flow bridge tests: no model download, no GPU, no network."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from PIL import Image

MODULE = Path(__file__).resolve().parent / "rdflow_monster_bridge.py"
spec = importlib.util.spec_from_file_location("rdflow_monster_bridge", MODULE)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

class TestSpritePipeline(unittest.TestCase):
    def test_valid_sprite_preserves_alpha_and_palette(self):
        im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for y in range(4, 60):
            for x in range(3, 61):
                im.putpixel((x, y), (x*3 % 256, y*4 % 256, 200, 255))
        sprite, meta = bridge.normalize(im)
        self.assertEqual(sprite.size, (64, 64))
        self.assertLessEqual(meta["total_colors"], 16)
        self.assertGreaterEqual(meta["normalized_bbox"][0], 2)
        self.assertGreaterEqual(meta["normalized_bbox"][1], 2)
        self.assertLessEqual(meta["normalized_bbox"][2], 62)
        self.assertLessEqual(meta["normalized_bbox"][3], 62)
        self.assertEqual(set(sprite.getchannel("A").getdata()), {0, 255})

    def test_reject_non_native_resolution(self):
        with self.assertRaises(ValueError):
            bridge.normalize(Image.new("RGBA", (512, 512), "red"))

    def test_reject_empty_and_opaque_background(self):
        with self.assertRaises(ValueError):
            bridge.normalize(Image.new("RGBA", (64, 64), (0, 0, 0, 0)))
        opaque = Image.new("RGBA", (64, 64), (220, 10, 10, 255))
        _, meta = bridge.normalize(opaque)
        self.assertEqual(meta["normalized_bbox"], [2, 2, 62, 62])

    def test_imported_sprite_cli_emits_png_and_metadata(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            src = Image.new("RGBA", (64, 64))
            for y in range(8, 48):
                for x in range(8, 48):
                    src.putpixel((x, y), (200, 50, 40, 255))
            src.save(root/"source.png")
            code = bridge.main(["--source-png", str(root/"source.png"),
                                "--name", "flame_beast", "--stage", "2",
                                "--out", str(root/"out")])
            self.assertEqual(code, 0)
            self.assertTrue((root/"out"/"flame_beast_s2_64.png").is_file())
            self.assertTrue((root/"out"/"flame_beast_s2_64.json").is_file())

if __name__ == "__main__":
    unittest.main()
