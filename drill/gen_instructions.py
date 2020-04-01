def filter(not_contain):
    global alias
    regex = []

    not_contain_regex = "("
    for c in not_contain:
        c = c.replace(".", "\.")
        not_contain_regex += ".*" + c + ".*"
        not_contain_regex += '|'

    not_contain_regex = not_contain_regex[:-1]
    not_contain_regex += ")"

    # we remove newlines first, because regexp_matches seems to not work correctyl for strings with newlines (at least could not get it done)
    return "SELECT * FROM " + alias + " WHERE NOT( regexp_matches(regexp_replace(writer_stacktrace,'(\\n)',''), '" + not_contain_regex + "') or regexp_matches( regexp_replace(reader_stacktrace,'(\\n)',''), '" + not_contain_regex + "') )"



def distinct():
    global alias
    return "SELECT DISTINCT location, reader_stacktrace, writer_stacktrace FROM " + alias


def fields():
    global alias
    return "SELECT * FROM " + alias + " WHERE parent IS NOT NULL"


def arrays():
    global alias
    return "SELECT * FROM " + alias + " WHERE parent IS NULL"


def locations():
    global alias
    return "SELECT DISTINCT location FROM " + alias


def writer_traces():
    global alias
    return "SELECT DISTINCT writer_stacktrace  FROM " + alias


def order_by(col):
    global alias
    return "SELECT * FROM " + alias + " ORDER BY " + col

def select(*argv):
    global alias

    cols=""
    for arg in argv:
        cols+=arg
        cols+=', '
    cols=cols[:-2]

    return "SELECT DISTINCT "+ cols+" FROM " + alias + " ORDER BY writer_thread_id, writer_th_clock"

def filter_epoch(val):
    global alias
    return "SELECT * FROM " + alias + " WHERE epoch=" + str(val)


def with_sql(from_clause, alias):
    return "WITH\n" + alias + " AS (SELECT * FROM " + from_clause + ")"


def concat_sql(a, b):
    global alias
    alias = alias + "1"
    return a + ",\n" + alias + " AS (" + b + ")"


def start_sql(from_clause, alias_l="t1"):
    global alias
    alias = alias_l
    return with_sql(from_clause, alias)


def end_sql(sql):
    global alias
    return sql + " SELECT * FROM " + alias


def as_table(query, table_name):
    return "alter session set `store.format`='json'; \n" + "CREATE TABLE rep.out.`" + table_name + "` AS (" + query + ")"


# framework idea: automatically pipeline arbitrary sql queries. DO NOT EMBED (subqueries) sql queries for easier reading. Therefore we use WITH and sequentially
# list the queries in execution order. The query itself remain simple because they have no subqueries and minimal from clauses.

# table
table = "./tracker_report_1442932176.json"

# table = "./test.json"
# namespace
namespace = "rep.root"
# from_clause
from_clause = namespace + ".`" + table + "`"

# --- basic filtering ---
query = start_sql(from_clause)
not_contain = ["edu.brown.cs.systems","org.apache.hadoop.hbase.zookeeper"]
query = concat_sql(query, filter(not_contain))
#query = concat_sql(query, filter_epoch(3))


def workflow_writer_chronological(query):
    query = concat_sql(query, order_by("writer_th_clock"))
    query = concat_sql(query, select("writer_th_clock","location"))
    query = end_sql(query)
    # query = as_table(query, "locations.json")
    return query

def workflow_locations(query):
    query = concat_sql(query, locations())
    query = concat_sql(query, order_by("location"))
    query = end_sql(query)
    # query = as_table(query, "locations.json")
    return query


def workflow_writer(query):
    query = concat_sql(query, writer_traces())
    query = concat_sql(query, order_by("writer_stacktrace"))
    query = end_sql(query)
    # query = as_table(query, "writers.json")
    return query


print(workflow_locations(query) + ";\n")

print(workflow_writer_chronological(query) + ";\n")

print(as_table("SELECT DISTINCT writer_stacktrace, reader_stacktrace FROM "+from_clause+" WHERE location='unknown'","read_writers.json")+";\n")
