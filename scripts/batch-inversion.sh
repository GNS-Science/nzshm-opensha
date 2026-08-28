#!/usr/bin/env bash
#
# Batch-run joint inversions: one InversionRunner JVM per config file, in sequence.
#
# Usage: scripts/batch-inversion.sh <configDir> <outputDir> [jarPath]
#
# Env overrides:
#   HEAP=55g          java max heap
#   JAVA=java         java executable
#   JAVA_OPTS=""      extra jvm flags
#
# Results per config land in <outputDir>/<configName>/ as solution.zip, stdout.log,
# stderr.log and a copy of the config. A config that already has a solution.zip is
# skipped, a failing run does not stop the batch.

# no `set -e`: a failing run must not abort the batch
set -u
set -o pipefail

MAIN_CLASS=nz.cri.gns.NZSHM22.opensha.inversion.joint.InversionRunner
HEAP=${HEAP:-55g}
JAVA=${JAVA:-java}
JAVA_OPTS=${JAVA_OPTS:-}

scriptDir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repoRoot=$(dirname "$scriptDir")

usage() {
    echo "Usage: $0 <configDir> <outputDir> [jarPath]" >&2
    exit 1
}

[ $# -ge 2 ] && [ $# -le 3 ] || usage

configDir=$1
outputDir=$2
jar=${3:-$repoRoot/build/libs/nzshm-opensha-all.jar}

if [ ! -d "$configDir" ]; then
    echo "No such config directory: $configDir" >&2
    exit 1
fi

if [ ! -f "$jar" ]; then
    echo "Jar not found: $jar" >&2
    echo "Build it with: (cd $repoRoot && ./gradlew fatJar)" >&2
    exit 1
fi

configs=()
while IFS= read -r c; do
    configs+=("$c")
done < <(find "$configDir" -maxdepth 1 -type f \( -name '*.json' -o -name '*.jsonc' \) | sort)

if [ ${#configs[@]} -eq 0 ]; then
    echo "No .json or .jsonc config files in $configDir" >&2
    exit 1
fi

mkdir -p "$outputDir" || exit 1

echo "configs:   ${#configs[@]}"
echo "jar:       $jar"
echo "heap:      $HEAP"
echo "outputDir: $outputDir"

names=()
results=()
failures=0

for config in "${configs[@]}"; do
    name=$(basename "$config")
    base=${name%.*}
    runDir="$outputDir/$base"
    solution="$runDir/solution.zip"
    names+=("$base")

    if [ -e "$solution" ]; then
        echo
        echo "=== $base: SKIPPED, $solution already exists"
        results+=("SKIPPED")
        continue
    fi

    mkdir -p "$runDir" || { results+=("FAILED (mkdir)"); failures=$((failures + 1)); continue; }
    cp "$config" "$runDir/$name"

    echo
    echo "=== $base: starting at $(date '+%Y-%m-%d %H:%M:%S')"
    start=$SECONDS

    "$JAVA" -Xmx"$HEAP" $JAVA_OPTS -cp "$jar" "$MAIN_CLASS" "$config" "$solution" \
        > "$runDir/stdout.log" 2> "$runDir/stderr.log"
    status=$?

    elapsed=$((SECONDS - start))
    if [ $status -eq 0 ]; then
        echo "=== $base: OK after ${elapsed}s -> $solution"
        results+=("OK (${elapsed}s)")
    else
        echo "=== $base: FAILED with exit code $status after ${elapsed}s, see $runDir/stderr.log"
        results+=("FAILED (exit $status)")
        failures=$((failures + 1))
    fi
done

echo
echo "=== summary"
for i in "${!names[@]}"; do
    printf '%-40s %s\n' "${names[$i]}" "${results[$i]}"
done

if [ $failures -gt 0 ]; then
    echo "$failures run(s) failed" >&2
    exit 1
fi
