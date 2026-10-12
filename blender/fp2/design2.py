import json, fp_author as fa
from author_run import *
S={}
def addz(name, rs, ls, extra=None, char="rukia"):
    sp={"char":char,"state":"sealed","pose":{"pos":[0,0,0],"rot":[0,0,0]},"arms":{"mode":"ik","right":{"on":True,"s":rs},"left":{"on":True,"s":ls}}}
    S[name]=sp
    print(name, fa.report(sp))
addz("z1",-0.06,0.12)
addz("z2",-0.06,0.16)
addz("z3",-0.10,0.10)
addz("z4",-0.06,0.12,char="byakuya")
render(S,"C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl")
for n in S: overlay("C:/Users/efeki/AppData/Local/Temp/claude/D--MineBleach/24a5dadf-3601-4df4-82f2-1e4f5be1fca0/scratchpad/bl/%s.png"%n)
