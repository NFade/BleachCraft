import json, fp_author as fa
from author_run import *
S={}
for name,(g,d,b,roll) in {
 "r0":((0.22,-0.45),1.05,(0.15,0.72,-0.68),0),
 "r40":((0.22,-0.45),1.05,(0.15,0.72,-0.68),40),
 "r-40":((0.22,-0.45),1.05,(0.15,0.72,-0.68),-40),
 "r90":((0.22,-0.45),1.05,(0.15,0.72,-0.68),90),
}.items():
    S[name]=spec_for("rukia","drawn",g,d,b,roll,-0.03,-0.134)
    print(name, S[name]["pose"], fa.report(S[name]))
render(S,"C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl")
