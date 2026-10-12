import json, fp_author as fa
from author_run import *
S={}
P={}
def add(name, state, g, d, b, roll, rs, ls):
    S[name]=spec_for("rukia", state, g, d, b, roll, rs, ls)
    P[name]=S[name]["pose"]
    print(name, [round(x,3) for x in S[name]["pose"]["pos"]], [round(x,1) for x in S[name]["pose"]["rot"]], fa.report(S[name]))
add("g1","drawn",(0.15,-0.25),1.1,(-0.50,0.30,-0.81),40,-0.03,-0.134)
add("g2","drawn",(0.25,-0.30),1.0,(-0.80,0.35,-0.50),40,-0.03,-0.134)
add("g3","drawn",(0.20,-0.35),1.0,(-0.65,0.55,-0.52),40,-0.03,-0.134)
add("g4","drawn",(0.18,-0.40),1.0,(-0.35,0.75,-0.55),40,-0.03,-0.134)
add("s1","sealed",(0.30,-0.55),0.95,(-0.55,0.05,-0.83),0,-0.06,0.12)
add("s2","sealed",(0.30,-0.50),0.95,(-0.45,0.20,-0.87),0,-0.06,0.12)
add("sg1","sealed",(0.20,-0.30),1.0,(-0.50,0.40,-0.77),0,-0.06,0.12)
add("sg2","sealed",(0.25,-0.35),1.0,(-0.75,0.30,-0.58),0,-0.06,0.12)
render(S,"C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl")
for n in S: overlay("C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl/%s.png"%n)
