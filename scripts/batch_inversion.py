#!/usr/bin/env python3
"""
Batch-run joint inversions: one InversionWithHazardCacheRunner JVM per config file, in
sequence. Each solution is written with its hazard map curves attached.

Usage: python scripts/batch_inversion.py <configDir> <outputDir> [jarPath] [options]

Options (each can also be set through the environment variable in brackets):
  --heap 55g          java max heap (HEAP)
  --java java         java executable (JAVA)
  --java-opts "..."   extra jvm flags, whitespace separated (JAVA_OPTS)
  --main-class ...    entry point (MAIN_CLASS);
                      nz.cri.gns.NZSHM22.opensha.inversion.joint.InversionRunner runs the
                      inversion without the hazard cache

Results per config land in <outputDir>/<configName>/ as solution.zip, stdout.log,
stderr.log and a copy of the config. A config that already has a solution.zip is
skipped, a failing run does not stop the batch.

Needs Python 3.6+ and nothing outside the standard library.
"""

import argparse
import os
import shlex
import shutil
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_MAIN_CLASS = "nz.cri.gns.NZSHM22.opensha.inversion.joint.InversionWithHazardCacheRunner"

repo_root = Path(__file__).resolve().parent.parent


def parse_args():
    parser = argparse.ArgumentParser(
        description="Batch-run joint inversions, one JVM per config file, in sequence."
    )
    parser.add_argument("config_dir", type=Path, help="directory with .json/.jsonc configs")
    parser.add_argument("output_dir", type=Path, help="directory the runs are written to")
    parser.add_argument(
        "jar",
        type=Path,
        nargs="?",
        default=repo_root / "build" / "libs" / "nzshm-opensha-all.jar",
        help="fat jar, defaults to build/libs/nzshm-opensha-all.jar",
    )
    parser.add_argument("--heap", default=os.environ.get("HEAP") or "55g")
    parser.add_argument("--java", default=os.environ.get("JAVA") or "java")
    parser.add_argument("--java-opts", default=os.environ.get("JAVA_OPTS") or "")
    parser.add_argument(
        "--main-class", default=os.environ.get("MAIN_CLASS") or DEFAULT_MAIN_CLASS
    )
    return parser.parse_args()


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def main():
    args = parse_args()

    if not args.config_dir.is_dir():
        fail("No such config directory: {}".format(args.config_dir))

    if not args.jar.is_file():
        fail(
            "Jar not found: {}\nBuild it with: (cd {} && ./gradlew fatJar)".format(
                args.jar, repo_root
            )
        )
    jar = args.jar.resolve()

    configs = sorted(
        p for p in args.config_dir.iterdir() if p.is_file() and p.suffix in (".json", ".jsonc")
    )
    if not configs:
        fail("No .json or .jsonc config files in {}".format(args.config_dir))

    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_dir = args.output_dir.resolve()
    java_opts = shlex.split(args.java_opts, posix=os.name != "nt")

    print("configs:   {}".format(len(configs)))
    print("main:      {}".format(args.main_class))
    print("jar:       {}".format(jar))
    print("heap:      {}".format(args.heap))
    print("outputDir: {}".format(output_dir))

    results = []
    failures = 0

    for config in configs:
        base = config.stem
        run_dir = output_dir / base
        solution = run_dir / "solution.zip"

        if solution.exists():
            print()
            print("=== {}: SKIPPED, {} already exists".format(base, solution))
            results.append((base, "SKIPPED"))
            continue

        try:
            run_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(str(config), str(run_dir / config.name))
        except OSError as e:
            print("=== {}: could not set up {}: {}".format(base, run_dir, e))
            results.append((base, "FAILED (mkdir)"))
            failures += 1
            continue

        print()
        print("=== {}: starting at {}".format(base, time.strftime("%Y-%m-%d %H:%M:%S")))
        sys.stdout.flush()
        start = time.monotonic()

        command = (
            [args.java, "-Xmx" + args.heap]
            + java_opts
            + ["-cp", str(jar), args.main_class, str(config.resolve()), str(solution)]
        )
        try:
            with open(str(run_dir / "stdout.log"), "wb") as out, open(
                str(run_dir / "stderr.log"), "wb"
            ) as err:
                status = subprocess.call(command, stdout=out, stderr=err)
        except OSError as e:
            print("=== {}: could not start {}: {}".format(base, args.java, e))
            status = 127

        elapsed = int(time.monotonic() - start)
        if status == 0:
            print("=== {}: OK after {}s -> {}".format(base, elapsed, solution))
            results.append((base, "OK ({}s)".format(elapsed)))
        else:
            print(
                "=== {}: FAILED with exit code {} after {}s, see {}".format(
                    base, status, elapsed, run_dir / "stderr.log"
                )
            )
            results.append((base, "FAILED (exit {})".format(status)))
            failures += 1

    print()
    print("=== summary")
    for name, result in results:
        print("{:<40} {}".format(name, result))

    if failures > 0:
        fail("{} run(s) failed".format(failures))


if __name__ == "__main__":
    main()
