import json, fp_author as fa
from author_run import *
S={}
for name,(g,d,b,roll) in {
 "st_e":((0.20,-0.52),0.95,(0.20,0.80,-0.55),0),
 "st_f":((0.20,-0.52),0.95,(0.25,0.70,-0.65),0),
 "st_g":((0.24,-0.50),0.95,(0.10,0.72,-0.68),0),
 "st_h":((0.24,-0.50),0.95,(0.0,0.66,-0.75),0),
}.items():
    S[name]=spec_for("rukia","drawn",g,d,b,roll,-0.03,-0.134)
    print(name, S[name]["pose"], fa.report(S[name]))
render(S,"C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl")
