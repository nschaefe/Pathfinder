
# table
table="./tracker_1714303664.json"
#namespace
namespace="rep.root"

# filtering based on stacktrace
not_contain = ["java.util.zip.ZipFile", "edu.brown.cs.systems"]
#TODO javassit
regex = []

not_contain_regex = "("
for c in not_contain:
    c = c.replace(".", "\.")
    not_contain_regex += c
    not_contain_regex += '|'

not_contain_regex = not_contain_regex[:-1]
not_contain_regex += ")"

s = f"SELECT * FROM {namespace}.`{table}` WHERE NOT(regexp_matches(writer_stacktrace,'{not_contain_regex}') or regexp_matches(reader_stacktrace,'{not_contain_regex}'));"
print(s)