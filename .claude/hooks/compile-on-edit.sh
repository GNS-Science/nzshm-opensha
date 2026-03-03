#!/bin/bash
# Run compileJava after editing a .java file.
# Outputs JSON so Claude sees the build result via additionalContext.

input=$(cat)
file=$(echo "$input" | grep -o '"file_path":"[^"]*"' | grep -o '[^"]*\.java$')
[[ -n "$file" ]] || exit 0

output=$(./gradlew spotlessApply compileJava 2>&1)
exit_code=$?

if [ $exit_code -ne 0 ]; then
    # Exit 2 feeds stderr to Claude as a blocking error
    echo "$output" >&2
    exit 2
fi

# Exit 0 with additionalContext so Claude sees the success
printf '{"additionalContext": "spotlessApply + compileJava after editing %s: BUILD SUCCESSFUL"}' "$file"
exit 0
