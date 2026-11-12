# SPDX-License-Identifier: LGPL-2.1-or-later
import argparse
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("genai_rebuild", Path(__file__).parents[1] / "rebuild_mat.py")
rebuild = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rebuild)


class RebuildCacheTest(unittest.TestCase):
    def test_unpinned_original_is_rejected_before_build_or_output_access(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = root / "wrong.aar"
            raw.write_bytes(b"not the pinned original artifact")
            output = root / "output" / "generated.aar"
            with self.assertRaisesRegex(RuntimeError, "Official GenAI AAR checksum mismatch"):
                rebuild.rebuild(argparse.Namespace(input=raw, output=output))
            self.assertFalse(output.parent.exists())

    def test_extracted_sdk_is_replaced_from_the_verified_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            name = f"cpp_client_telemetry-{rebuild.SDK_VERSION}"
            archive = root / f"{name}.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr(f"{name}/CMakeLists.txt", b"verified source")
            source = root / name
            source.mkdir()
            (source / "CMakeLists.txt").write_bytes(b"corrupted source")
            (source / "unreviewed.cpp").write_bytes(b"stale injected file")
            pinned = hashlib.sha256(archive.read_bytes()).hexdigest()
            with patch.object(rebuild, "SDK_SHA256", pinned):
                restored = rebuild.fetch_sdk(root)
            self.assertEqual(source, restored)
            self.assertEqual(b"verified source", (source / "CMakeLists.txt").read_bytes())
            self.assertFalse((source / "unreviewed.cpp").exists())
            self.assertEqual(pinned, hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_bad_cached_and_downloaded_sdk_never_reaches_extraction(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / f"cpp_client_telemetry-{rebuild.SDK_VERSION}.zip"
            archive.write_bytes(b"bad cached archive")
            with patch.object(rebuild.urllib.request, "urlopen", side_effect=lambda *a, **k: io.BytesIO(b"bad download")), patch.object(rebuild.time, "sleep"):
                with self.assertRaisesRegex(RuntimeError, "Telemetry source archive checksum mismatch"):
                    rebuild.fetch_sdk(root)
            self.assertFalse((root / f"cpp_client_telemetry-{rebuild.SDK_VERSION}").exists())

    def test_atomic_write_replaces_the_complete_file_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "artifact.aar"
            target.write_bytes(b"previous")
            rebuild.atomic_write(target, b"complete replacement")
            self.assertEqual(b"complete replacement", target.read_bytes())
            self.assertEqual([target], list(root.iterdir()))

    def test_failed_replace_preserves_previous_file_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "artifact.aar"
            target.write_bytes(b"previous valid artifact")
            with patch.object(rebuild.os, "replace", side_effect=OSError("replace blocked")):
                with self.assertRaises(OSError):
                    rebuild.atomic_write(target, b"replacement")
            self.assertEqual(b"previous valid artifact", target.read_bytes())
            self.assertEqual([target], list(root.iterdir()))


if __name__ == "__main__":
    unittest.main()
