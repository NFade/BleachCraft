#!/bin/bash
# usage: b4run.sh <item> <set> ; runs one tune set in the dev harness, copies pngs to $OUT (default scratch)
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" GRADLE_USER_HOME='D:\gradle-home'
SP="C:/Users/Haru/AppData/Local/Temp/claude/C--/1a7011d3-a928-4148-9a4f-0880766e66c7/scratchpad"
cd "$(dirname "$0")/../mod"
python -I ../tools/spike_tune_gen.py "$SP/$2.json" $2 >/dev/null
rm -f run/screenshots/spike_tune_*.png
timeout 590 ./gradlew runSpike -Pitem=$1 -Ptune="$SP/$2.json" --console=plain > "$SP/run_$2.log" 2>&1
grep -E "BUILD|ERROR|Exception" "$SP/run_$2.log" | head -5
ls run/screenshots | grep spike_tune | wc -l
