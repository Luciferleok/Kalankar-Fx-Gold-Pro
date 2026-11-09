#!/usr/bin/env bash
# V37-A: JVM regression tests for the app's decision / ledger / display logic (no Android device needed).
#
# What runs: every tools/jvm-tests/tests/*.kt is a small program with main(). It is compiled against the app's own
# data/, model/ and localization/ sources (plus a few tiny stubs in stubs/ for Android-only libraries) and run.
# All fixtures are SYNTHETIC. This proves the logic; it does NOT prove the Android UI or real market accuracy.
#
# A test passes when it exits 0 and its last line says "N passed, 0 failed" (or "PASS"). The two audit programs that
# document known, still-open defects must print exactly their expected count (EXPECTED below): if a count changes,
# someone fixed or broke something and must update this file on purpose.
#
# Usage (from anywhere):  tools/jvm-tests/run.sh
#   KOTLIN_HOME  folder of the Kotlin compiler (kotlinc/bin, kotlinc/lib); default: kotlinc on PATH
#   JOBS         parallel jobs (default: number of CPUs)
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
if [ -n "${KOTLIN_HOME:-}" ]; then KC="$KOTLIN_HOME/bin/kotlinc"; KR="$KOTLIN_HOME/bin/kotlin"; LIB="$KOTLIN_HOME/lib"
else KC=$(command -v kotlinc); KR=$(command -v kotlin); LIB="$(dirname "$(dirname "$(readlink -f "$KC")")")/lib"; fi
CO="$LIB/kotlinx-coroutines-core-jvm.jar"
[ -f "$CO" ] || { echo "kotlinx-coroutines-core-jvm.jar not found in $LIB"; exit 2; }
OUT="$ROOT/build/jvm-tests"; rm -rf "$OUT"; mkdir -p "$OUT"
S=app/src/main/java/com/example/livegoldai

echo "== compiling app logic"
"$KC" $S/data $S/model $S/localization tools/jvm-tests/stubs -cp "$CO" -d "$OUT/logic.jar" > "$OUT/logic.compile.txt" 2>&1 \
  || { grep -E "error:" "$OUT/logic.compile.txt" | head -20; echo "APP LOGIC DOES NOT COMPILE"; exit 1; }

declare -A EXPECTED=( [R4c_AuditTest]="REPRODUCED DEFECTS: 2 of 9" [V340b_ForensicTest]="DEFECTS: 4 of 14" )
export KC KR CO OUT
one() {
  n=$(basename "$1" .kt)
  if ! "$KC" "$1" -cp "$OUT/logic.jar:$CO" -d "$OUT/$n.jar" > "$OUT/$n.compile.txt" 2>&1; then echo "$n|COMPILE-FAIL"; return; fi
  timeout 300 "$KR" -cp "$OUT/logic.jar:$CO:$OUT/$n.jar" "${n}Kt" > "$OUT/$n.out" 2>&1; code=$?
  echo "$n|$code|$(grep -v JAVA_TOOL "$OUT/$n.out" | tail -1)"
}
export -f one
echo "== running $(ls tools/jvm-tests/tests/*.kt | wc -l) test programs"
ls tools/jvm-tests/tests/*.kt | xargs -P "${JOBS:-$(nproc)}" -I{} bash -c 'one {}' | sort > "$OUT/summary.txt"

bad=0
while IFS='|' read -r n code last; do
  if [ "$code" = "COMPILE-FAIL" ]; then echo "FAIL  $n: does not compile"; grep -E "error:" "$OUT/$n.compile.txt" | head -5; bad=$((bad+1)); continue; fi
  if [ -n "${EXPECTED[$n]:-}" ]; then
    if [ "$code" = "0" ] && [ "$last" = "${EXPECTED[$n]}" ]; then echo "ok    $n: $last (known open defects, report only)"
    else echo "FAIL  $n: expected '${EXPECTED[$n]}', got '$last' (exit $code)"; bad=$((bad+1)); fi
    continue
  fi
  if [ "$code" = "0" ] && { echo "$last" | grep -Eq '[0-9]+ passed, 0 failed$' || [ "${last##*: }" = "PASS" ]; }; then echo "ok    $n: $last"
  else echo "FAIL  $n (exit $code): $last"; grep -E "^FAIL" "$OUT/$n.out" | head -5; bad=$((bad+1)); fi
done < "$OUT/summary.txt"

total=$(wc -l < "$OUT/summary.txt")
echo "== $((total-bad)) of $total test programs passed"
[ "$bad" -eq 0 ]
