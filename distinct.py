import sys
import mmh3
path = sys.argv[1]
#path = "./tracker_report"
path_out = path+"_distinct"
buff = ""
s = set()

with open(path, "r") as fp_in:
    with open(path_out, "w") as fp_out:
        line = fp_in.readline()
        while line:
            if "CONCURRENT WRITE/READ DETECTED" in line:
                ha = mmh3.hash128(buff, 42)
                if ha not in s:
                    s.add(ha)
                    fp_out.write(buff+"\n")

                buff = line
            else:
                buff += line

            line = fp_in.readline()
