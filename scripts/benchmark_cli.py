#!/usr/bin/env python3
"""Measure fresh CLI processes with identical temporary fixtures and cached filesystem data."""

import argparse
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    artifact = parser.add_mutually_exclusive_group(required=True)
    artifact.add_argument("--jar", type=Path)
    artifact.add_argument("--binary", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--iterations", type=int, default=5)
    args = parser.parse_args()
    if args.iterations < 1:
        parser.error("--iterations must be positive")
    command = ([args.java, "-jar", str(args.jar.resolve())] if args.jar
               else [str(args.binary.resolve())])
    environment = dict(os.environ, NO_COLOR="1", NO_EMOJI="1")
    results = {}

    with tempfile.TemporaryDirectory(prefix="dotbot-benchmark-") as directory:
        root = Path(directory)
        for count in (1000, 10000):
            (root / f"plan-{count}.json").write_text(json.dumps([
                {"link": {f"dest/{i}": f"source/{i}" for i in range(count)}}
            ]))
        source = root / "source"
        source.mkdir()
        for index in range(5000):
            (source / f"file-{index:05d}").touch()
        glob_config = root / "glob.json"
        glob_config.write_text(json.dumps([{"link": {"dest": {
            "path": "source/*", "glob": True, "create": True,
            "exclude": [f"source/skip-{i}*" for i in range(10)],
        }}}]))
        scenarios = {
            "version": ["--version"],
            "help": ["--help"],
            "plan_json_1000": ["plan", "-c", str(root / "plan-1000.json"), "--output", "json"],
            "validate_10000": ["validate", "-c", str(root / "plan-10000.json")],
            "glob_dry_run_5000_excludes_10": ["-q", "--dry-run", "-c", str(glob_config)],
        }
        for name, flags in scenarios.items():
            samples = []
            for iteration in range(args.iterations + 1):
                start = time.perf_counter()
                result = subprocess.run(command + flags, stdout=subprocess.DEVNULL,
                                        stderr=subprocess.PIPE, env=environment, timeout=60)
                elapsed = (time.perf_counter() - start) * 1000
                if result.returncode:
                    raise RuntimeError(f"{name}: {result.stderr.decode(errors='replace')}")
                if iteration:
                    samples.append(round(elapsed, 2))
            results[name] = {"median_ms": round(statistics.median(samples), 2), "samples_ms": samples}

    runtime = (subprocess.run([args.java, "-version"], capture_output=True, text=True, check=True)
               .stderr.splitlines()[0] if args.jar else "native")
    print(json.dumps({"platform": platform.platform(), "runtime": runtime,
                      "artifact_bytes": (args.jar or args.binary).stat().st_size,
                      "iterations": args.iterations, "results": results}, indent=2))


if __name__ == "__main__":
    main()
