import copy
import psycopg2 as pg
from argparse import ArgumentParser
import logging
import datetime
import pyproj
from shapely.geometry import LineString, Point
import time

class InfDateTimeAdapter:
    def __init__(self, wrapped):
        self.wrapped = wrapped
    def getquoted(self):
        try:
            if self.wrapped == datetime.datetime.max:
                return b"'infinity'::timestamptz"
            elif self.wrapped == datetime.datetime.min:
                return b"'-infinity'::timestamptz"
        except:
            pass
        return pg.extensions.TimestampFromPy(self.wrapped).getquoted()

pg.extensions.register_adapter(datetime.datetime, InfDateTimeAdapter)


# Query postgres to get dictionary of Shapely LineStrings for each shape.
def get_shape_dict_from_pg(cur, proj, version_id = None):
    shape_dict = {}
    if version_id is None or version_id == 'temp':
        if version_id is None:
            table_name = 'gtfs_shapes'
        else:
            table_name = 'gtfs_shapes_temp'
        qry = """SELECT
        shape_id, shape_pt_lat, shape_pt_lon,
        shape_pt_sequence, shape_dist_traveled
        FROM %s
        ORDER BY shape_id, shape_pt_sequence""" % (table_name)
        cur.execute(qry)
    else:
        qry = """SELECT
        shape_id, shape_pt_lat, shape_pt_lon,
        shape_pt_sequence, shape_dist_traveled
        FROM gtfs_shapes_history
        CROSS JOIN gtfs_version
        WHERE version_id = %s
        AND t_start <@ t_range
        ORDER BY shape_id, shape_pt_sequence"""
        cur.execute(qry, (version_id, ))
        
    for row in cur:
        shape_id = row[0]
        if shape_id not in shape_dict:
            shape_dict[shape_id] = []
        shape_dict[shape_id].append(list(row[1:]))

    linestring_dict = {}
    for shape_id in shape_dict:
        coords = []
        for row in shape_dict[shape_id]:
            coords.append([row[1], row[0]])
            
        lon, lat = zip(*coords)
        x, y = proj(lon, lat)
        ls = LineString(zip(x,y))
        linestring_dict[shape_id] = ls
        
    return linestring_dict


# Query postgres to get dictionary of Shapely Points for each stop.
def get_stop_dict_from_pg(cur, version_id = None):
    stop_dict = {}
    if version_id is None or version_id == 'temp':
        if version_id is None:
            table_name = 'gtfs_stops'
        else:
            table_name = 'gtfs_stops_temp'
        qry = """SELECT
        stop_id, stop_lat, stop_lon
        FROM %s""" % (table_name)
        cur.execute(qry)
    else:
        qry = """SELECT
        stop_id, stop_lat, stop_lon
        FROM gtfs_stops_history
        CROSS JOIN gtfs_version
        WHERE version_id = %s
        AND t_start <@ t_range"""
        cur.execute(qry, (version_id, ))
    
    for row in cur:
        stop_id = row[0]
        pt = Point(row[2], row[1])
        stop_dict[stop_id] = pt
        
    return stop_dict


# Query postgres to get dictionary of stop times keyed by trip id.
def get_trip_dict_from_pg(cur, version_id = None):
    trip_dict = {}
    if version_id is None or version_id == 'temp':
        if version_id is None:
            table_name_stop_times = 'gtfs_stop_times'
            table_name_trips = 'gtfs_trips'
        else:
            table_name_stop_times = 'gtfs_stop_times_temp'
            table_name_trips = 'gtfs_trips_temp'
        qry = """SELECT
        st.trip_id,
        shape_id,
        stop_sequence,
        stop_id,
        shape_dist_traveled,
        true
        FROM %s st
        JOIN %s t
        ON st.trip_id = t.trip_id
        ORDER BY st.trip_id, stop_sequence""" % (table_name_stop_times,
        table_name_trips)
        cur.execute(qry)
    else:
        qry = """SELECT
        sth.trip_id,
        shape_id,
        stop_sequence,
        stop_id,
        shape_dist_traveled,
        upper(sth.t_range) = 'infinity'::timestamptz
        FROM gtfs_stop_times_history sth
        JOIN gtfs_trips_history th
        ON sth.trip_id = th.trip_id
        CROSS JOIN gtfs_version
        WHERE version_id = %s
        AND t_start <@ th.t_range
        AND t_start <@ sth.t_range
        ORDER BY sth.trip_id, stop_sequence"""
        cur.execute(qry, (version_id, ))
    
    for row in cur:
        trip_id = row[0]
        if trip_id not in trip_dict:
            trip_dict[trip_id] = []
        trip_dict[trip_id].append(list(row[1:]))
        
    return trip_dict


def create_temp_table(cur, temp_table_name):
    stmt = """
    CREATE TEMP TABLE %s (
        trip_id text,
        stop_sequence double precision,
        shape_dist_traveled double precision,
        current boolean
    );
    """ % temp_table_name
    cur.execute(stmt)


def load_to_temp_table(cur, temp_table_name, trip_dict, logger):
    trip_id_list = [tid for tid in trip_dict]
    trip_count = len(trip_id_list)

    args_str_list = []
    upload_count = 0

    trips_per_upload = 250
    tot_uploads = int(trip_count / trips_per_upload) + 1
    logger.info('%d uploads to perform' % (tot_uploads))
    
    for i in range(trip_count):
        trip_id = trip_id_list[i]
        st_list = trip_dict[trip_id]
        args_str = ','.join(cur.mogrify('(%s, %s, %s, %s)',
                                        (trip_id, st[1], st[3], st[4]))
                            for st in st_list)
        args_str_list.append(args_str)
        
        if len(args_str_list) == trips_per_upload or i == trip_count - 1:
            upload_count += 1
            args = ','.join(args_str_list)
            if len(args) > 0:
                cur.execute('INSERT INTO ' + temp_table_name + ' VALUES ' + args)
            args_str_list = []
            logger.info('Performed upload #%d' % (upload_count))

    logger.warning('All uploads complete.')


def disable_triggers(cur):
    stmt = """
    ALTER TABLE gtfs_stop_times
    DISABLE TRIGGER gtfs_stop_times_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_stop_times
    DISABLE TRIGGER gtfs_stop_times_history"""
    cur.execute(stmt)


def enable_triggers(cur):
    stmt = """
    ALTER TABLE gtfs_stop_times
    ENABLE TRIGGER gtfs_stop_times_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_stop_times
    ENABLE TRIGGER gtfs_stop_times_history"""
    cur.execute(stmt)


def update_pg_postmile(cur, temp_table_name, version_id = None):
    if version_id == 'temp':
        st_name = 'gtfs_stop_times_temp'
    else:
        st_name = 'gtfs_stop_times'
    stmt = """
    UPDATE %s st
    SET shape_dist_traveled = tt.shape_dist_traveled
    FROM %s tt
    WHERE st.trip_id = tt.trip_id
    AND st.stop_sequence = tt.stop_sequence
    AND current
    """ % (st_name, temp_table_name)

    cur.execute(stmt)


# Cuts a line in two at a distance from its starting point. Borrowed from
# http://toblerity.org/shapely/manual.html.
def cut(line, distance):
    if distance <= 0.0 or distance >= line.length:
        return [LineString(line)]
    coords = list(line.coords)
    pd = None
    for i, p in enumerate(coords):
        # use this method instead of project to protect against bivalued
        # shape-points
        if i == 0:
            pd = 0.0
        else:
            p_prev = coords[i-1]
            d = Point(p_prev).distance(Point(p))
            pd += d

        if pd == distance:
            return [
                LineString(coords[:i+1]),
                LineString(coords[i:])]
        if pd > distance:
            cp = line.interpolate(distance)
            return [
                LineString(coords[:i] + [(cp.x, cp.y)]),
                LineString([(cp.x, cp.y)] + coords[i:])]


def get_post_key(shape_id, st_list):
    stop_id_list = [st[2] for st in st_list]
    stops_str = '='.join(stop_id_list)
    if shape_id is not None:
        return '='.join([shape_id, stops_str])
    else:
        return stops_str


def get_existing_postmiles(shape_id, st_list, proj_dict):
    post_key = get_post_key(shape_id, st_list)
    if post_key in proj_dict:
        return proj_dict[post_key]
    else:
        return None


def add_postmiles(shape_id, st_list, proj_dict):
    post_key = get_post_key(shape_id, st_list)
    posts = [st[3] for st in st_list]
    proj_dict[post_key] = posts


def add_existing_postmile_to_trip(st_list, postmiles):
    for i in range(len(st_list)):
        st_list[i][3] = postmiles[i]


def add_postmile_to_trip(trip_id, shape_ls, st_list, stop_dict, proj, logger):
    cut_shape = shape_ls
    prev_dst = 0.0
    tot_len = shape_ls.length

    for i, st in enumerate(st_list):
        stop_id = st[2]
        stop = stop_dict[stop_id]

        if i > 0:
            # Use straight-line distance between previous stop and this one to
            # define cut size.
            prev_st = st_list[i-1]
            prev_stop_id = prev_st[2]
            prev_stop = stop_dict[prev_stop_id]
            delta = prev_stop.distance(stop)/3.
        else:
            if len(st_list) > 1:
                # Use straight-line distance between this stop and the next to
                # define cut size.
                next_st = st_list[1]
                next_stop_id = next_st[2]
                next_stop = stop_dict[next_stop_id]
                delta = next_stop.distance(stop)/3.

            else:
                # Use arbitrary fraction of route.
                delta = tot_len / 10.

        # In general, make sure that we're not jumping too far or not far
        # enough.
        if delta > tot_len / 10.:
            delta = tot_len / 10.
        elif delta < 50:
            delta = 50

        target_dist = 50

        d_max = delta
        dist = target_dist + 100
        while dist > target_dist and d_max < cut_shape.length + delta:
            # Loop over intervals starting from the beginning of the shape
            # looking for a good match. Each time we don't get a good fit,
            # add delta meters more to the subshape. This ensures that we don't
            # "skip" a good fit for a slightly better one that is too much
            # further along the shape.
            sub_shape = cut(cut_shape, d_max)[0]

            d_proj = sub_shape.project(stop)
            proj_pt = sub_shape.interpolate(d_proj)

            dist = stop.distance(proj_pt)

            d_max += delta

        # One more interval for good measure (to avoid annoying cutoff effects):
        sub_shape_f = cut(cut_shape, d_max)[0]
        d_proj_f = sub_shape_f.project(stop)
        proj_pt_f = sub_shape_f.interpolate(d_proj_f)
        dist_f = stop.distance(proj_pt_f)
        if dist_f < dist:
            d_proj = d_proj_f

        shapes = cut(cut_shape, d_proj)
        if len(shapes) > 1:
            cut_shape = shapes[1]
        else:
            cut_shape = shapes[0]

        st[3] = d_proj + prev_dst
        prev_dst += d_proj

    problema = False
    eps = 0.001
    for st0, st1 in zip(st_list[:-1], st_list[1:]):
        if st0[3] > st1[3]-eps:
            sid0 = st0[2]
            sid1 = st1[2]
            s0 = stop_dict[sid0]
            s1 = stop_dict[sid1]
            lat0, lon0 = s0.y, s0.x
            lat1, lon1 = s1.y, s1.x
            if (abs(lat0 - lat1) > eps or abs(lon0 - lon1) > eps):
                problema = True
                logger.info("Bad pair of stop times")
                logger.info(st0)
                logger.info(st1)
            break

    if problema:
        logger.info("Bad trip: %s" % (trip_id))
        for st in st_list:
            logger.info(st)

    return not problema


def generate_shape_from_stops(trip_list, stop_dict):
    coords = []
    for st in trip_list:
        stop_id = st[2]
        stop = stop_dict[stop_id]
        coords.append([stop.x, stop.y])

    ls = LineString(coords)
    return ls


def get_shape(shape_id, st_list, shape_dict, stop_dict):
    shape_ls = None
    if shape_id is None:
        # Build arbitrary but reproduceable shape id for these cases.
        stop_id_list = [st[2] for st in st_list]
        shape_id = '='.join(stop_id_list)

        if shape_id not in shape_dict:
            # No corresponding shape. Build a shape out of the stop locations.
            shape_ls = generate_shape_from_stops(st_list, stop_dict)
            shape_dict[shape_id] = shape_ls
        else:
            shape_ls = shape_dict[shape_id]

    elif shape_id not in shape_dict:
        # No corresponding shape. Build a shape out of the stop locations.
        shape_ls = generate_shape_from_stops(st_list, stop_dict)
        shape_dict[shape_id] = shape_ls
    else:
        shape_ls = shape_dict[shape_id]
    return shape_ls


def add_all_postmiles(shape_dict, stop_dict, trip_dict, proj, logger):
    postmiles_dict = {}
    count = 0
    unique_count = 0
    bad_count = 0
    tot = len(trip_dict)
    for trip_id in trip_dict:
        count += 1
        st_list = trip_dict[trip_id]
        shape_id = st_list[0][0]
        shape_ls = get_shape(shape_id, st_list, shape_dict, stop_dict)

        postmiles = get_existing_postmiles(shape_id, st_list, postmiles_dict)
        if postmiles is None:
            logger.debug("new trip with trip_id: %s" % trip_id)
            t_start = time.time()
            good = add_postmile_to_trip(trip_id, shape_ls, st_list, stop_dict,
                                        proj, logger)

            unique_count += 1
            if not good:
                bad_count += 1

            add_postmiles(shape_id, st_list, postmiles_dict)
            t_end = time.time()
            logger.debug("Took %.2f seconds to add postmiles." % (t_end-t_start))
        else:
            logger.debug("old trip with trip_id: %s" % trip_id)
            add_existing_postmile_to_trip(st_list, postmiles)
        if count % 250 == 0:
            logger.info("%d out of %d trips projected" % (count, tot))
            logger.info("(%d bad out of %d unique)" %
                        (bad_count, unique_count))


def get_proj(stop_dict):
    lat_min, lat_max, lon_min, lon_max = None, None, None, None
    for stop_id in stop_dict:
        pt = stop_dict[stop_id]
        if lat_min is None:
            lat_min = pt.y
            lon_min = pt.x
            lat_max = lat_min
            lon_max = lon_min
        else:
            lat = pt.y
            if lat < lat_min:
                lat_min = lat
            if lat > lat_max:
                lat_max = lat

            lon = pt.x
            if lon < lon_min:
                lon_min = lon
            if lon > lon_max:
                lon_max = lon

    pa = pyproj.Proj("+proj=aea +lat_0=%f +lon_0=%f +lat_1=%f +lon_1=%f"
                     % (lat_min, lon_min, lat_max, lon_max))
    return pa


def project_stops(stop_dict, proj):
    for stop_id in stop_dict:
        pt = stop_dict[stop_id]
        x, y = proj(pt.x, pt.y)
        stop_dict[stop_id] = Point(x,y)


# Assumes that cur is a postgres cursor. Populates shapes, stops, and lists of
# stop times corresponding to trips.
def populate_st_postmiles(cur, logger, version_id=None):
    logger.info('Disabling triggers')
    disable_triggers(cur)

    logger.info('Populating stop info')
    stop_dict = get_stop_dict_from_pg(cur, version_id)
    logger.info('%d total stops' % (len(stop_dict)))

    logger.info('Defining planar projection')
    pa = get_proj(stop_dict)

    logger.info('Projecting stops')
    project_stops(stop_dict, pa)

    logger.info('Populating shape info')
    shape_dict = get_shape_dict_from_pg(cur, pa, version_id)
    logger.info('%d total shapes' % (len(shape_dict)))

    logger.info('Populating trips with stop times')
    trip_dict = get_trip_dict_from_pg(cur, version_id)
    logger.info('%d total trips' % (len(trip_dict)))

    logger.info('Adding shape_dist_traveled')
    add_all_postmiles(shape_dict, stop_dict, trip_dict, pa, logger)

    temp_table_name = 'gtfs_stop_times_fixed'
    logger.info('Inserting to temp table %s' % temp_table_name)
    create_temp_table(cur, temp_table_name)
    load_to_temp_table(cur, temp_table_name, trip_dict, logger)
    logger.info('Updating shape_dist_traveled in gtfs_stop_times')
    update_pg_postmile(cur, temp_table_name, version_id)
    logger.info('Done updating gtfs_stop_times!')

    if version_id != 'temp':
        logger.info("Rewriting history.")
        rewrite_history(cur, temp_table_name, version_id)

    logger.info('Reenabling triggers')
    enable_triggers(cur)


def rewrite_history(cur, temp_table_name, version_id = None):
    t_start = None
    if version_id is None:
        qry = "SELECT t_start FROM gtfs_version ORDER BY t_start DESC LIMIT 1"
        cur.execute(qry)
        for row in cur:
            t_start = row[0]
    else:
        qry = "SELECT t_start FROM gtfs_version WHERE version_id = %s"
        cur.execute(qry, (version_id, ))
        for row in cur:
            t_start = row[0]

    stmt = """
    UPDATE gtfs_stop_times_history sth
    SET shape_dist_traveled = tt.shape_dist_traveled
    FROM %s tt
    WHERE sth.trip_id = tt.trip_id
    AND sth.stop_sequence = tt.stop_sequence
    AND %s <@ sth.t_range
    """ % (temp_table_name, '%s')
    
    cur.execute(stmt, (t_start, ))


if __name__ == "__main__":
    parser = ArgumentParser()

    parser.add_argument("-a", "--agency-name", dest="agency", 
                        metavar="AGENCY",
                        required=True,
                        help="name of agency")
    parser.add_argument("-L", "--log-level", dest="loglevel", 
                        metavar="LOGLEVEL",
                        default="INFO",
                        help="log level of Predictor (one of CRITICAL, ERROR, WARNING, INFO, or DEBUG)",
                        choices=('CRITICAL', 'ERROR', 'WARNING', 'INFO', 'DEBUG'))
    parser.add_argument("--postgres-host", dest="pg_host",
                        required=True,
                        metavar="POSTGRES_HOST",
                        help="URI for postgres")
    parser.add_argument("--postgres-user", dest="pg_user",
                        required=True,
                        metavar="POSTGRES_USER",
                        help="username for postgres")
    parser.add_argument("--postgres-pass", dest="pg_pass",
                        required=True,
                        metavar="POSTGRES_PASS",
                        help="password for postgres")
    parser.add_argument("--gtfs-version", dest="gtfs_version",
                        metavar="GTFS_VERSION",
                        help="""version id of GTFS schedule to modify.
                        If none, use most recent version. If 'temp', it is
                        assumed that we're cleaning temp tables which have
                        been populated with a schedule yet to be inserted
                        into the main table.""")
    parser.add_argument("--write-mode", dest="write",
                        action='store_true',
                        help="run script in write mode (actually write to postgres)")
    parser.set_defaults(write=False)
    args = parser.parse_args()

    logger_name = '%s_stop_shape_dist_traveled' % (args.agency)
    
    logger = logging.getLogger(logger_name)

    logLevel = getattr(logging, args.loglevel)
    logging.basicConfig(format='%(asctime)s %(message)s',
                        level=logLevel)
    
    conn_str = "dbname='%s' host='%s' user='%s' password='%s'" % (args.agency,
                                                                  args.pg_host,
                                                                  args.pg_user,
                                                                  args.pg_pass)
    conn = pg.connect(conn_str)
    cur = conn.cursor()

    try:
        logger.info('Updating stop_times for %s' % (args.agency))
        populate_st_postmiles(cur, logger, args.gtfs_version)
        logger.info('all done!')
        if args.write:
            logger.warning('Committing changes.')
            conn.commit()
        else:
            logger.warning('Rolling back (debug mode).')
            conn.rollback()
    except:
        logger.exception("Failed to add shape_dist_traveled to stop times!")
        conn.rollback()
    finally:
        conn.close()
