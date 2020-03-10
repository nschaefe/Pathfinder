def filter1(not_contain, table):
    # TODO javassit
    regex = []

    not_contain_regex = "("
    for c in not_contain:
        c = c.replace(".", "\.")
        not_contain_regex += c
        not_contain_regex += '|'

    not_contain_regex = not_contain_regex[:-1]
    not_contain_regex += ")"

    return "SELECT * FROM " + table + " WHERE NOT(regexp_matches(writer_stacktrace, '" + not_contain_regex + "') or regexp_matches(reader_stacktrace, '" + not_contain_regex + "'))"


def filter(not_contain):
    global alias
    return filter1(not_contain, alias)


def distinct1(table):
    return "SELECT DISTINCT location, reader_stacktrace, writer_stacktrace FROM " + table


def distinct():
    global alias
    return distinct1(alias)


def no_arrays():
    global alias
    return "SELECT * FROM " + alias + " WHERE parent IS NOT NULL"


def locations1(table):
    return "SELECT DISTINCT location FROM " + table


def locations():
    global alias
    return locations1(alias)


def writer_traces():
    global alias
    return "SELECT DISTINCT writer_stacktrace  FROM " + alias


def order_by(col):
    global alias
    return "SELECT * FROM " + alias + " ORDER BY "+col


def with_sql(from_clause, alias):
    return "WITH " + alias + " AS (SELECT * FROM " + from_clause + ")"


def concat_sql(a, b):
    global alias
    alias = alias + "1"
    return a + ", " + alias + " AS (" + b + ")"


def start_sql(from_clause, alias_l="t1"):
    global alias
    alias = alias_l
    return with_sql(from_clause, alias)


def end_sql(sql):
    global alias
    return sql + " SELECT * FROM " + alias


def as_table(query, table_name):
    return "alter session set `store.format`='json'; \n" + "CREATE TABLE rep.out.`" + table_name + "` AS (" + query + ")"


#java.util.logging.Logger
#.ajc$tjp_0
#org.apache.hadoop.hbase.trace

# framework idea: automatically pipeline arbitrary sql queries. DO NOT EMBED sql queries for easier reading. Therefore we use WITH and sequentially
# list the queries in execution order. The query itself remain simple because they have no subqueries and minimal from clauses.

# table
table = "./tracker_report_1193542123.json"
# namespace
namespace = "rep.root"
# from_clause
from_clause = namespace + ".`" + table + "`"

# TODO
#not_contain_location = ["org.apache.hbase.thirdparty.io.netty"]

query = start_sql(from_clause)
query = concat_sql(query, distinct())

not_contain = ["java.util.zip.ZipFile", "edu.brown.cs.systems"]
query = concat_sql(query, filter(not_contain))

query = end_sql(query)
query = as_table(query, "filtered.json")

print(query + ";")

query = start_sql(from_clause)
query = concat_sql(query, no_arrays())
query = concat_sql(query, writer_traces())
query = concat_sql(query, order_by("writer_stacktrace"))
query = end_sql(query)
#query = as_table(query, "field_locs.json")
print(query + ";")
