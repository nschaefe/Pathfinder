import sys
path = sys.argv[1]
#path="./tracker_report"
f = open(path, "r")
lines = f.readlines() 
f.close()

s = set()
buff = ""

for line in lines: 
    if "CONCURRENT WRITE/READ DETECTED" in line :
        s.add(buff)
        buff = line
    else:
        buff += line


path = path+"_distinct"
f = open(path, 'w')
f.write('\n'.join(s))
f.close()
