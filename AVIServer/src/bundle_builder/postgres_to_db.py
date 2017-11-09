import psycopg2 as pg
import sqlite3
from argparse import ArgumentParser
import logging

def mkstrlist(raw):
    return [el.strip() for el in raw.split(',')]


def prepare_good_trips(pg_conn, route_id_list, these_routes_only):
    # Create temp table of acceptable trip IDs in Postgres
    if route_id_list is None:
        # All trips on all routes.
        qry = """
        CREATE TEMP TABLE good_trips_final AS
        SELECT trip_id
        FROM gtfs_trips
        """
        cur = pg_conn.cursor()
        cur.execute(qry)
        cur.close()
    elif these_routes_only:
        # All trips on our routes only.
        qry = """
        CREATE TEMP TABLE good_trips_final AS
        SELECT trip_id
        FROM gtfs_trips
        WHERE route_id in (%s)
        """ % (', '.join(["%s" for _ in route_id_list]))
        cur = pg_conn.cursor()
        cur.execute(qry, tuple(route_id_list))
        cur.close()
    else:
        # All trips on our routes, plus for other routes in blocks that have
        # trips on our routes (block overlap), plus for other trips on other
        # routes that share stops with our routes (stop overlap).
        qry = """
        CREATE TEMP TABLE good_trips AS
        SELECT trip_id, block_id
        FROM gtfs_trips
        WHERE route_id in (%s)
        """ % (', '.join(["%s" for _ in route_id_list]))
        cur = pg_conn.cursor()
        cur.execute(qry, tuple(route_id_list))
        cur.close()

        qry = """
        CREATE TEMP TABLE trips_in_blocks AS
        SELECT distinct(t.trip_id)
        FROM gtfs_trips t
        JOIN good_trips gt
        ON gt.block_id = t.block_id
        """
        cur = pg_conn.cursor()
        cur.execute(qry)
        cur.close()

        qry = """
        CREATE TEMP TABLE stops_in_trips AS
        SELECT distinct(stop_id)
        FROM gtfs_stop_times st
        JOIN good_trips gt
        ON gt.trip_id = st.trip_id
        """
        cur = pg_conn.cursor()
        cur.execute(qry)
        cur.close()

        qry = """
        CREATE TEMP TABLE trips_at_stops AS
        SELECT distinct(trip_id)
        FROM gtfs_stop_times st
        WHERE stop_id IN
        (SELECT distinct(stop_id) FROM stops_in_trips);
        """
        cur = pg_conn.cursor()
        cur.execute(qry)
        cur.close()

        qry = """
        CREATE TEMP TABLE good_trips_final AS
        (SELECT trip_id FROM trips_at_stops)
        UNION
        (SELECT trip_id FROM trips_in_blocks)
        """
        cur = pg_conn.cursor()
        cur.execute(qry)
        cur.close()

        
def prepare_good_shapes(pg_conn):
    qry = """
    CREATE TEMP TABLE good_shapes AS
    SELECT distinct(s.shape_id)
    FROM gtfs_shapes s
    JOIN gtfs_trips t
    ON s.shape_id = t.shape_id
    JOIN good_trips_final gt
    ON gt.trip_id = t.trip_id
    """
    cur = pg_conn.cursor()
    cur.execute(qry)
    cur.close()

    
def prepare_good_services(pg_conn):
    qry = """
    CREATE TEMP TABLE good_services AS
    SELECT distinct(service_id)
    FROM gtfs_trips t
    JOIN good_trips_final gt
    ON gt.trip_id = t.trip_id
    """
    cur = pg_conn.cursor()
    cur.execute(qry)
    cur.close()

    
def prepare_good_stops(pg_conn):
    qry = """
    CREATE TEMP TABLE good_stops AS
    SELECT distinct(stop_id)
    FROM gtfs_stop_times st
    JOIN gtfs_trips t
    ON st.trip_id = t.trip_id
    JOIN good_trips_final gt
    ON gt.trip_id = t.trip_id
    """
    cur = pg_conn.cursor()
    cur.execute(qry)
    cur.close()

    
def download_shapes(pg_conn, db_conn, logger):
    logger.info("Downloading shapes from Postgres")
    qry = """
    SELECT s.shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence,
    shape_dist_traveled
    FROM gtfs_shapes s
    JOIN good_shapes gs
    ON s.shape_id = gs.shape_id
    ORDER BY s.shape_id, shape_pt_sequence
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create shapes table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE shapes (shape_id TEXT, shape_pt_lat NUMERIC,
    shape_pt_lon NUMERIC, shape_pt_sequence INTEGER,
    shape_dist_traveled NUMERIC)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO shapes (shape_id, shape_pt_lat, shape_pt_lon,
        shape_pt_sequence, shape_dist_traveled)
        VALUES (?, ?, ?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting shapes")
    cur.close()


def download_trips(pg_conn, db_conn, logger):
    logger.info("Downloading trips from Postgres")
    qry = """
    SELECT t.trip_id, route_id, service_id,
    block_id, shape_id, direction_id
    FROM gtfs_trips t
    JOIN good_trips_final gt
    ON t.trip_id = gt.trip_id
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create trips table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE trips (trip_id TEXT, route_id TEXT, service_id TEXT,
    block_id TEXT, shape_id TEXT, direction_id INTEGER)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO trips (trip_id, route_id, service_id,
        block_id, shape_id, direction_id)
        VALUES (?, ?, ?, ?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting trips")
    cur.close()


def download_stops(pg_conn, db_conn, logger):
    logger.info("Downloading stops from Postgres")
    qry = """
    SELECT s.stop_id, stop_lat, stop_lon
    FROM gtfs_stops s
    JOIN good_stops gs
    ON s.stop_id = gs.stop_id
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create trips table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE stops (stop_id TEXT, stop_lat NUMERIC, stop_lon NUMERIC)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO stops (stop_id, stop_lat, stop_lon)
        VALUES (?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting stops")
    cur.close()


def download_stop_times(pg_conn, db_conn, logger):
    logger.info("Downloading stop_times from Postgres")
    qry = """
    SELECT st.trip_id, stop_sequence, stop_id, arrival_time, departure_time,
    shape_dist_traveled
    FROM gtfs_stop_times st
    JOIN good_trips_final gt
    ON st.trip_id = gt.trip_id
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create trips table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE stop_times (trip_id TEXT, stop_sequence INTEGER,
    stop_id TEXT, arrival_time TEXT, departure_time TEXT,
    shape_dist_traveled NUMERIC)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO stop_times (trip_id, stop_sequence, stop_id, arrival_time,
        departure_time, shape_dist_traveled)
        VALUES (?, ?, ?, ?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting stop_times")
    cur.close()


def download_calendar(pg_conn, db_conn, logger):
    logger.info("Downloading calendar from Postgres")
    qry = """
    SELECT c.service_id, monday, tuesday, wednesday, thursday, friday,
    saturday, sunday, start_date, end_date
    FROM gtfs_calendar c
    JOIN good_services gs
    ON c.service_id = gs.service_id
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create trips table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE calendar (service_id TEXT, monday INTEGER, tuesday INTEGER,
    wednesday INTEGER, thursday INTEGER, friday INTEGER, saturday INTEGER,
    sunday INTEGER, start_date TEXT, end_date TEXT)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO calendar (service_id, monday, tuesday, wednesday,
        thursday, friday, saturday, sunday, start_date, end_date)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting calendar")
    cur.close()


def download_calendar_dates(pg_conn, db_conn, logger):
    logger.info("Downloading calendar_dates from Postgres")
    qry = """
    SELECT cd.service_id, date, exception_type
    FROM gtfs_calendar_dates cd
    JOIN good_services gs
    ON cd.service_id = gs.service_id
    """

    cur = pg_conn.cursor()
    cur.execute(qry)
    data = []
    for row in cur:
        data.append(row)
    cur.close()

    logger.info("Inserting %d rows into db file" % len(data))

    # Create trips table in sqlite db
    cur = db_conn.cursor()
    qry = """
    CREATE TABLE calendar_dates (service_id TEXT, date TEXT,
    exception_type INTEGER)
    """
    cur.execute(qry)

    for row in data:
        qry = """
        INSERT INTO calendar_dates (service_id, date, exception_type)
        VALUES (?, ?, ?)
        """
        cur.execute(qry, row)
    
    db_conn.commit()
    logger.info("Done inserting calendar_dates")
    cur.close()

    
def ensure_empty_db(db_conn):
    # Ensure that relevant tables don't exist yet
    for table in ['shapes', 'trips', 'stop_times', 'calendar',
                  'calendar_dates', 'stops']:
        qry = """
        SELECT count(*) FROM sqlite_master WHERE type = 'table'
        AND name = ?
        """
        res = db_conn.execute(qry, (table,))
        for row in res:
            if row[0] > 0:
                raise Exception('Table %s already exists! Aborting.' % table)


def write_indexes(db_conn, logger):
    qry = """
    CREATE INDEX stop_times_trip_id_stop_sequence
    ON stop_times (trip_id, stop_sequence);
    """

    logger.info("Writing common indexes")
    
    cur = db_conn.cursor()
    cur.execute(qry)
    db_conn.commit()
    cur.close()

    
if __name__=='__main__':
    parser = ArgumentParser()
    parser.add_argument("--db-out", dest="db_out",
                        metavar="DB_OUT",
                        required=True,
                        help="path to output db (e.g. /path/to/database.db")
    parser.add_argument("--agency", dest="agency", 
                        metavar="AGENCY",
                        required=True,
                        help="name of agency")
    parser.add_argument("--pg-host", dest="pg_host", 
                        metavar="PG_HOST",
                        required=True,
                        help="hostname of Postgres database")
    parser.add_argument("--pg-user", dest="pg_user", 
                        required=True,
                        metavar="PG_USER",
                        help="username for Postgres database")
    parser.add_argument("--pg-pass", dest="pg_pass", 
                        required=True,
                        metavar="PG_PASS",
                        help="password for Postgres database")
    parser.add_argument("--route-id-list", dest="route_id_list",
                        metavar="ROUTE_ID_LIST",
                        default=None,
                        type=mkstrlist,
                        help="list of routes to populate schedules for (e.g., 8,18,29)")
    parser.add_argument("--indexes", dest="indexes",
                        action='store_true',
                        help="add indexes to speed common queries")
    parser.add_argument("--these-routes-only", dest="these_routes_only",
                        action='store_true',
                        help="only store data for these routes, not for routes with overlap")
    parser.set_defaults(indexes=False)
    parser.add_argument("-L", "--log-level", dest="log_level", 
                        metavar="LOGLEVEL",
                        default="INFO",
                        help="log level of Predictor (one of CRITICAL, ERROR, WARNING, INFO, or DEBUG)",
                        choices=('CRITICAL', 'ERROR', 'WARNING', 'INFO', 'DEBUG'))

    args = parser.parse_args()
    
    logger_name = "postgres_to_db"
    log_level = getattr(logging, args.log_level)
    logging.basicConfig(format='%(asctime)s [%(module)s.%(funcName)s] %(message)s',
                        level=log_level)
    logger = logging.getLogger(logger_name)

    # Open local db file
    db_conn = sqlite3.connect(args.db_out)
    ensure_empty_db(db_conn)

    # Connect to Postgres
    conn_str = 'host=\'{0}\' dbname=\'{1}\' user=\'{2}\' password=\'{3}\''
    conn_str = conn_str.format(args.pg_host, args.agency, 
                               args.pg_user, args.pg_pass)
    pg_conn = pg.connect(conn_str)

    # prepare temp tables to store relevant trips, shapes, services, stops
    logger.info("Preparing temp tables")
    prepare_good_trips(pg_conn, args.route_id_list, args.these_routes_only)
    prepare_good_shapes(pg_conn)
    prepare_good_services(pg_conn)
    prepare_good_stops(pg_conn)
    logger.info("Temp tables created")

    # download relevant tables into db file
    download_calendar(pg_conn, db_conn, logger)
    download_calendar_dates(pg_conn, db_conn, logger)
    download_stops(pg_conn, db_conn, logger)
    download_shapes(pg_conn, db_conn, logger)
    download_trips(pg_conn, db_conn, logger)
    download_stop_times(pg_conn, db_conn, logger)

    if args.indexes:
        write_indexes(db_conn, logger)
