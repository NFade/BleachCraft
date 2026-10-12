import json, fp_author as fa
from author_run import *
S={}
# stance candidates: grip ndc, depth, blade dir, roll
for name,(g,d,b,roll) in {
 "st_a":((0.18,-0.55),0.95,(0.20,0.80,-0.55),0),
 "st_b":((0.18,-0.55),0.95,(0.22,0.60,-0.77),0),
 "st_c":((0.18,-0.55),0.95,(0.22,0.45,-0.86),0),
 "st_d":((0.22,-0.50),0.90,(0.15,0.70,-0.70),20),
}.items():
    S[name]=spec_for("rukia","drawn",g,d,b,roll,0.005,-0.115)
    print(name, S[name]["pose"], fa.report(S[name]))
render(S,"C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl")
