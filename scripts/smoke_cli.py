#!/usr/bin/env python3
"""Exercise the shipped artifact, including permissions under a restrictive process umask."""

import argparse
import json
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    artifact = parser.add_mutually_exclusive_group(required=True)
    artifact.add_argument("--jar", type=Path)
    artifact.add_argument("--binary", type=Path)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    command = ([args.java, "-jar", str(args.jar.resolve())] if args.jar
               else [str(args.binary.resolve())])

    def run(*flags, expected=0, private_umask=False):
        invocation = command + list(flags)
        if private_umask:
            invocation = ["/bin/sh", "-c", 'umask 077; exec "$@"', "dotbot-smoke"] + invocation
        result = subprocess.run(invocation, text=True, capture_output=True, timeout=15)
        assert result.returncode == expected, (flags, result.returncode, result.stdout, result.stderr)
        assert "Exception:" not in result.stderr, result.stderr
        return result

    assert "version" in run("--version").stdout.lower()
    assert "Usage:" in run("--help").stdout
    with tempfile.TemporaryDirectory(prefix="dotbot-artifact-smoke-") as directory:
        root = Path(directory)
        config = root / "install.json"

        def write(tasks):
            config.write_text(json.dumps(tasks))

        write([{"create": ["private"]}])
        run("-c", str(config), private_umask=True)
        assert (root / "private").stat().st_mode & 0o777 == 0o700

        (root / "source").write_text("replacement")
        (root / "dest").write_text("original")
        write([{"link": {"dest": {"path": "source", "relink": True}}}])
        run("--dry-run", "-c", str(config), expected=1)
        run("-c", str(config), expected=1)
        assert (root / "dest").read_text() == "original"

        name = "d" * 240
        (root / name).write_text("preserve me")
        write([{"link": {name: {"path": "source", "backup": True, "force": True}}}])
        run("-c", str(config), expected=1)
        assert not (root / name).is_symlink()
        assert (root / name).read_text() == "preserve me"

        for defaults in (42, {"link": 42}, {"lnik": {"force": True}}):
            write([{"defaults": defaults}])
            run("validate", "-c", str(config), expected=1)

        write([{"link": {"dest": {"path": "bad[", "glob": True}}}])
        run("validate", "-c", str(config), expected=1)
        run("-c", str(config), expected=1)

        formats = {
            "yaml": "- create: [uncreated]\n",
            "json": '[{"create":["uncreated"]}]',
            "toml": '[[tasks]]\ncreate = ["uncreated"]\n',
            "conf": 'tasks = [{create = ["uncreated"]}]\n',
        }
        for extension, content in formats.items():
            path = root / f"format.{extension}"
            path.write_text(content)
            run("validate", "-c", str(path))
            plan = json.loads(run("plan", "--output", "json", "-c", str(path)).stdout)
            assert plan["operation_count"] == 1
            assert not (root / "uncreated").exists()

    print("Packaged CLI smoke checks passed: four parsers, plan JSON, permissions, backups, validation, and dry-run.")


if __name__ == "__main__":
    main()
