#!/bin/bash
SP="C:/Users/Haru/AppData/Local/Temp/claude/C--/1a7011d3-a928-4148-9a4f-0880766e66c7/scratchpad"
OUT="$SP/final"; rm -rf "$OUT"; mkdir -p "$OUT"
cd "$(dirname "$0")"
for pair in "sode_no_shirayuki k1" "sode_no_shirayuki k2" "sode_no_shirayuki kh" "sode_no_shirayuki k5" "senbonzakura q1" "senbonzakura q2" "senbonzakura qh" "senbonzakura q5"; do
  set -- $pair
  ./b4run.sh $1 $2
  cp ../mod/run/screenshots/spike_tune_*.png "$OUT/"
done
echo ALLDONE > "$OUT/done.txt"
