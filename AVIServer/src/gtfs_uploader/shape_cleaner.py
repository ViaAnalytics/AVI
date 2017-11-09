import pyproj
from shapely.geometry import LineString, Point
import copy
import psycopg2 as pg
from psycopg2.extras import DateTimeTZRange
from argparse import ArgumentParser
import logging
import datetime
from hashlib import md5

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

def cart_dist(x1, y1, x2, y2):
    ls = LineString([(x1, y1), (x2, y2)])
    return ls.length
    
def closest_pt_on_segment(x, y, x1, y1, x2, y2):
    ls = LineString([(x1, y1), (x2, y2)])
    pt = Point(x, y)
    pt_proj = ls.interpolate(ls.project(pt))
    return pt_proj.x, pt_proj.y

# Given a list of points, returns the maximum distance from any point to
# the line joining the endpoints, as well as the index of that point. Points
# are assumed to be in a Cartesian coordinate system.
def get_dmax_and_index(pl):
    dmax = 0.
    index = 0
    
    for i in range(1, len(pl)-1):
        x, y = pl[i][0], pl[i][1]
        xp, yp = closest_pt_on_segment(x, y, pl[0][0], pl[0][1],
                                       pl[-1][0], pl[-1][1])
        dist = cart_dist(x, y, xp, yp)

        if dist > dmax:
            index = i
            dmax = dist

    return dmax, index

# Recursive Ramey-Douglas-Peucker algorithm. Modeled after wikipedia
# psuedocode.
def rdp(pt_list, eps_meter):
    dmax, index = get_dmax_and_index(pt_list)

    if dmax > eps_meter:
        pl_left = rdp(pt_list[:index+1], eps_meter)
        pl_right = rdp(pt_list[index:], eps_meter)

        return pl_left[:-1] + pl_right
    else:
        return [pt_list[0], pt_list[-1]]


def gen_proj(lat1, lon1, lat2, lon2):
    return pyproj.Proj("+proj=aea +lat_0=%f +lon_0=%f +lat_1=%f +lon_1=%f"
                       % (lat1, lon1, lat2, lon2))


# Loop through shapes and set shape_dist_traveled in meters.
# Mark these shape points as requiring an update.
def add_shape_dist_traveled(shape_dict):
    for shape_id in shape_dict:
        d = 0.0
        prev_lat = 0.0
        prev_lon = 0.0
        for i, row in enumerate(shape_dict[shape_id]):
            lat = row[0]
            lon = row[1]
            if i > 0:
                pa = gen_proj(lat, lon, prev_lat, prev_lon)
                x, y = pa(lon, lat)
                xp, yp = pa(prev_lon, prev_lat)
                dist = cart_dist(x, y, xp, yp)
                d += dist
            # Update shape_dist_traveled, and note that this row needs to
            # be updated in postgres.
            shape_dict[shape_id][i][3] = d
            shape_dict[shape_id][i][4] = True

            prev_lat = lat
            prev_lon = lon
            
    return shape_dict


# Apply RDP algorithm to determine redundant shape points, and mark for
# deletion.
def remove_redundant_shape_points(shape_dict, eps_meter, logger):
    cleaned_shape_dict = {}
    shape_ids = sorted(shape_dict.keys())

    # Generate single projection to use for entire agency data -- this
    # sacrifices some accuracy for speed, and will work best for agencies that
    # are not incredibly huge.
    lat_min, lat_max = 500., -500.
    lon_min, lon_max = 500., -500.
    all_lats = []
    all_lons = []
    locs = {}
    i_start = 0
    for shape_id in shape_ids:
        lat_lons = [(pt[0], pt[1]) for pt in shape_dict[shape_id]]
        for lat, lon in lat_lons:
            if lat < lat_min:
                lat_min = lat
            if lat > lat_max:
                lat_max = lat
            if lon < lon_min:
                lon_min = lon
            if lon > lon_max:
                lon_max = lon

        # prepare data structures for transform in parallel
        n_pts = len(lat_lons)
        locs[shape_id] = (i_start, i_start + n_pts)
        i_start += n_pts
        lats = [lat for lat, lon in lat_lons]
        lons = [lon for lat, lon in lat_lons]
        all_lats.extend(lats)
        all_lons.extend(lons)

    # perform coordinate transform
    pa = gen_proj(lat_min, lon_min, lat_max, lon_max)
    all_new_lons, all_new_lats = pa(all_lons, all_lats)

    # use transformed coordinates (and switch x and y)
    for shape_id in shape_ids:
        loc = locs[shape_id]
        shape_new_lats = all_new_lats[loc[0]:loc[1]]
        shape_new_lons = all_new_lons[loc[0]:loc[1]]
        for i, pt in enumerate(shape_dict[shape_id]):
            pt[0], pt[1] = shape_new_lons[i], shape_new_lats[i]

    for shape_id in shape_ids:
        logger.debug("Working on shape %s" % shape_id)
        
        loc = locs[shape_id]
        shape_orig_lats = all_lats[loc[0]:loc[1]]
        shape_orig_lons = all_lons[loc[0]:loc[1]]

        res = rdp(shape_dict[shape_id], eps_meter)
        cleaned_shape_dict[shape_id] = []
        for i, row in enumerate(shape_dict[shape_id]):
            # use original coordinates
            row[0], row[1] = shape_orig_lats[i], shape_orig_lons[i]
            sps = row[2]
            keeping = False
            for clean_row in res:
                if sps == clean_row[2]:
                    cleaned_shape_dict[shape_id].append(clean_row)
                    keeping = True
                    break
            if not keeping:
                row[5] = True
                cleaned_shape_dict[shape_id].append(row)
        old_len = len(shape_dict[shape_id])
        new_len = len(cleaned_shape_dict[shape_id])
        logger.debug("Shape %s shortened from %d pts to %d pts" %
                     (shape_id, old_len, new_len))

        if len(cleaned_shape_dict) % 25 == 0:
            logger.info("Cleaned %d shapes" % len(cleaned_shape_dict))

    return cleaned_shape_dict


# Convenience function to print stats about redundancy removal results.
def print_clean_stats(shape_dict, cleaned_shape_dict, logger):
    tot_rows_i = 0
    tot_rows_f = 0
    for shape_id in cleaned_shape_dict:
        logger.debug(shape_id)
        rows_i = len(shape_dict[shape_id])
        rows_f = len([pt for pt in cleaned_shape_dict[shape_id]
                          if pt[5]==False])
        # rows_f = len(cleaned_shape_dict[shape_id])
        logger.debug("original length: %d" % rows_i)
        logger.debug("final length: %d" % rows_f)
        tot_rows_i += rows_i
        tot_rows_f += rows_f
    logger.info("Cleaned %d total shapes" % len(cleaned_shape_dict))
    logger.info("Decreasing total shape points from %s to %s" % (tot_rows_i,
                                                                 tot_rows_f))
    if tot_rows_i > 0:
        logger.info("Total decrease of %f percent" %
                    (100.*(1. - 1.*tot_rows_f/tot_rows_i)))
    else:
        logger.info("No shapefile rows.")


def table_exists(cur, table_str):
    exists = False
    try:
        cur.execute("select exists(select relname from pg_class where relname='" + table_str + "')")
        exists = cur.fetchone()[0]
    except psycopg2.Error as e:
        print e
    return exists


# Query postgres to get dictionary of lists of shape points.
def get_shape_dict_from_postgres(cur, version_id = None):
    shape_dict = {}
    if version_id is None or version_id == 'temp':
        if version_id is None:
            table_name = 'gtfs_shapes'
        else:
            table_name = 'gtfs_shapes_temp'
            
            if not table_exists(cur, table_name):
                create_tmp_table_stmt = """
                CREATE TEMP TABLE %s
                (LIKE %s
                INCLUDING DEFAULTS INCLUDING INDEXES)
                ON COMMIT DROP""" % ('gtfs_shapes_temp', 'gtfs_shapes')
                
                cur.execute(create_tmp_table_stmt)
                return {}

        qry = """SELECT
        shape_id,
        shape_pt_lat,
        shape_pt_lon,
        shape_pt_sequence,
        shape_dist_traveled,
        true
        FROM %s
        ORDER BY shape_id, shape_pt_sequence""" % (table_name)
        cur.execute(qry)
    else:
        qry = """SELECT
        shape_id,
        shape_pt_lat,
        shape_pt_lon,
        shape_pt_sequence,
        shape_dist_traveled,
        upper(t_range) = 'infinity'::timestamptz
        FROM gtfs_shapes_history sh
        CROSS JOIN gtfs_version v
        WHERE version_id = %s
        AND t_start <@ t_range
        ORDER BY shape_id, shape_pt_sequence"""
        
        cur.execute(qry, (version_id, ))
        
    for row in cur:
        shape_id = row[0]
        if shape_id not in shape_dict:
            shape_dict[shape_id] = []
        # final four booleans indicate 'to update', 'delete', 'insert',
        # 'current', respectively
        shape_dict[shape_id].append(list(row[1:5]) + [False, False, False, row[5]])
    return shape_dict


# Query postgres to get dictionary of lists of shape points.
def get_nonshapes_from_postgres(cur, version_id=None):
    if version_id is None or version_id == 'temp':
        if version_id is None:
            t_name = 'gtfs_trips'
            st_name = 'gtfs_stop_times'
            s_name = 'gtfs_stops'
        else:
            t_name = 'gtfs_trips_temp'
            st_name = 'gtfs_stop_times_temp'
            s_name = 'gtfs_stops_temp'
        st_dict = {}
        qry = """SELECT
        t.trip_id AS trip_id,
        s.stop_id AS stop_id,
        stop_lat,
        stop_lon,
        stop_sequence,
        true
        FROM %s t
        JOIN %s st ON t.trip_id = st.trip_id
        JOIN %s s ON st.stop_id = s.stop_id
        WHERE t.shape_id IS NULL
        ORDER BY t.trip_id, stop_sequence""" % (t_name, st_name, s_name)
        cur.execute(qry)
    else:
        st_dict = {}
        qry = """SELECT
        th.trip_id AS trip_id,
        sh.stop_id AS stop_id,
        stop_lat,
        stop_lon,
        stop_sequence,
        upper(th.t_range) = 'infinity'::timestamptz
        FROM gtfs_trips_history th
        JOIN gtfs_stop_times_history sth ON th.trip_id = sth.trip_id
        JOIN gtfs_stops_history sh ON sth.stop_id = sh.stop_id
        CROSS JOIN gtfs_version v
        WHERE version_id = %s
        AND th.shape_id IS NULL
        AND t_start <@ th.t_range
        AND t_start <@ sth.t_range
        AND t_start <@ sh.t_range
        ORDER BY th.trip_id, stop_sequence"""
        cur.execute(qry, (version_id, ))
        
    for row in cur:
        trip_id = row[0]
        if trip_id not in st_dict:
            st_dict[trip_id] = []
        st_dict[trip_id].append(list(row[1:]))
    return st_dict


def create_temp_table(cur, temp_table_name):
    stmt = """
    CREATE TEMP TABLE %s (
        shape_id text,
        shape_pt_lat double precision,
        shape_pt_lon double precision,
        shape_pt_sequence integer,
        shape_dist_traveled double precision,
        to_update boolean,
        to_delete boolean,
        to_insert boolean,
        current boolean
    );
    """ % temp_table_name
    cur.execute(stmt)


def load_to_temp_table(cur, temp_table_name, cleaned_shape_dict, logger):
    shape_id_list = [sid for sid in cleaned_shape_dict]
    shape_count = len(shape_id_list)

    shapes_per_upload = 100
    tot_uploads = int(shape_count/shapes_per_upload) + 1
    logger.info('%d uploads to perform.' % (tot_uploads))

    args_str_list = []
    upload_count = 0
    for i in range(shape_count):
        shape_id = shape_id_list[i]
        rows = cleaned_shape_dict[shape_id]
        args_str = ','.join(cur.mogrify('(%s, %s, %s, %s, %s, %s, %s, %s, %s)',
                                        (shape_id,)+tuple(x)) for x in rows)
        args_str_list.append(args_str)
        if len(args_str_list) == shapes_per_upload or i == shape_count - 1:
            upload_count += 1
            args = ','.join(args_str_list)
            if len(args) > 0:
                cur.execute('INSERT INTO ' + temp_table_name + ' VALUES ' + args)
            args_str_list = []
            logger.info('Performed upload #%d' % (upload_count))
    logger.info('Finished uploads!')


def disable_triggers(cur):
    stmt = """
    ALTER TABLE gtfs_shapes
    DISABLE TRIGGER gtfs_shapes_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_shapes
    DISABLE TRIGGER gtfs_shapes_history"""
    cur.execute(stmt)


def enable_triggers(cur):
    stmt = """
    ALTER TABLE gtfs_shapes
    ENABLE TRIGGER gtfs_shapes_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_shapes
    ENABLE TRIGGER gtfs_shapes_history"""
    cur.execute(stmt)


def rewrite_history(cur, temp_table_name, version_id=None):
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

    # delete old points
    stmt = """
    DELETE FROM gtfs_shapes_history sh
    USING %s tt
    WHERE sh.shape_id = tt.shape_id
    AND sh.shape_pt_sequence = tt.shape_pt_sequence
    AND to_delete
    AND %s <@ sh.t_range
    """ % (temp_table_name, '%s')
    
    cur.execute(stmt, (t_start, ))

    # update good points
    stmt = """
    UPDATE gtfs_shapes_history sh
    SET shape_dist_traveled = tt.shape_dist_traveled
    FROM %s tt
    WHERE sh.shape_id = tt.shape_id
    AND sh.shape_pt_sequence = tt.shape_pt_sequence
    AND to_update
    AND not to_delete
    AND %s <@ sh.t_range
    """ % (temp_table_name, '%s')
    
    cur.execute(stmt, (t_start, ))

    # insert new points
    
    stmt = """
    INSERT INTO gtfs_shapes_history (t_range, id, shape_id,
    shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled)
    (SELECT %s, s.id, s.shape_id, s.shape_pt_lat, s.shape_pt_lon,
    s.shape_pt_sequence, s.shape_dist_traveled
    FROM %s tt JOIN gtfs_shapes s
    ON s.shape_id = tt.shape_id AND s.shape_pt_sequence = tt.shape_pt_sequence
    WHERE to_insert)
    """ % ('%s', temp_table_name)

    new_t_range = DateTimeTZRange(t_start, datetime.datetime.max)
    
    cur.execute(stmt, (new_t_range, ))

    # No need to update gtfs_trips_history, because we never disabled
    # those triggers.


def update_shape_dist_traveled(cur, temp_table_name, version_id=None):
    if version_id == 'temp':
        s_name = 'gtfs_shapes_temp'
    else:
        s_name = 'gtfs_shapes'
    stmt = """
    UPDATE %s s
    SET shape_dist_traveled = tt.shape_dist_traveled
    FROM %s tt
    WHERE s.shape_id = tt.shape_id
    AND s.shape_pt_sequence = tt.shape_pt_sequence
    AND to_update
    AND not to_delete
    AND current
    """ % (s_name, temp_table_name)
    
    cur.execute(stmt)


def delete_shape_points(cur, temp_table_name, version_id=None):
    if version_id == 'temp':
        s_name = 'gtfs_shapes_temp'
    else:
        s_name = 'gtfs_shapes'
    stmt = """
    DELETE FROM %s s
    USING %s tt
    WHERE s.shape_id = tt.shape_id
    AND s.shape_pt_sequence = tt.shape_pt_sequence
    AND to_delete
    AND current
    """ % (s_name, temp_table_name)
    
    cur.execute(stmt)

    
def insert_new_shapes(cur, temp_table_name, version_id=None):
    if version_id == 'temp':
        s_name = 'gtfs_shapes_temp'
    else:
        s_name = 'gtfs_shapes'
    stmt = """
    INSERT INTO %s (shape_id, shape_pt_lat,
    shape_pt_lon, shape_pt_sequence, shape_dist_traveled)
    (SELECT shape_id, shape_pt_lat, shape_pt_lon,
    shape_pt_sequence, shape_dist_traveled
    FROM %s
    WHERE to_insert
    AND current)
    """ % (s_name, temp_table_name)
    
    cur.execute(stmt)


def add_shapeless_trips_to_shape_dict(shape_dict, shapeless_st_dict):
    trip_shape_map = {}

    for trip_id in shapeless_st_dict:
        st_list = shapeless_st_dict[trip_id]
        if len(st_list) == 0:
            # No stop times for this trip, so we can't even create a shape.
            # Give up.
            continue
        
        stop_id_string = "+".join([el[0] for el in st_list])

        shape_id = md5(stop_id_string).hexdigest()[0:16]
        # tuple is (shape_id, current):
        trip_shape_map[trip_id] = (shape_id, st_list[0][4])
        
        if shape_id in shape_dict:
            # We've already processed this particular list of stops
            continue

        # lat, lon, sequence, sdt, to_update, to_delete, to_insert, current
        shape_list = [row[1:4] + [None, False, False, True, row[4]] for row in st_list]
        shape_dict[shape_id] = shape_list

    return trip_shape_map


def update_shapeless_trips(cur, trip_shape_map, version_id=None):
    stmt = """
    CREATE TEMP TABLE trip_shape_map (
        trip_id text,
        shape_id text,
        current boolean
    );
    """

    cur.execute(stmt)
    
    args_str_list = []
    for trip_id in trip_shape_map:
        shape_id, current = trip_shape_map[trip_id]
        args_str = cur.mogrify('(%s, %s, %s)', (trip_id, shape_id, current))
        args_str_list.append(args_str)

    args = ','.join(args_str_list)
    if len(args) > 0:
        cur.execute('INSERT INTO trip_shape_map VALUES ' + args)

    # disable triggers
    stmt = """
    ALTER TABLE gtfs_trips
    DISABLE TRIGGER gtfs_trips_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_trips
    DISABLE TRIGGER gtfs_trips_history"""
    cur.execute(stmt)
    
    if version_id == 'temp':
        t_name = 'gtfs_trips_temp'
    else:
        t_name = 'gtfs_trips'
    stmt = """
    UPDATE %s t SET shape_id = tsm.shape_id
    FROM trip_shape_map tsm
    WHERE t.trip_id = tsm.trip_id
    AND current
    """ % (t_name)

    cur.execute(stmt)
    if version_id != 'temp':
        # rewrite history only if we're not working on the temp tables
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

        qry = """
        UPDATE gtfs_trips_history th SET shape_id = tsm.shape_id
        FROM trip_shape_map tsm
        WHERE th.trip_id = tsm.trip_id
        AND %s <@ t_range
        """
        cur.execute(qry, (t_start, ))

    stmt = """
    ALTER TABLE gtfs_trips
    ENABLE TRIGGER gtfs_trips_no_redundant_updates"""
    cur.execute(stmt)
    stmt = """
    ALTER TABLE gtfs_trips
    ENABLE TRIGGER gtfs_trips_history"""
    cur.execute(stmt)

# Assumes that shapes are already in the database and that cur is a postgres
# cursor. Removes unnecessary shape points and adds shape_dist_traveled if it
# doesn't already exist.
def clean_shapes(cur, eps_meter, logger, version_id=None):
    logger.info('Disabling triggers')
    disable_triggers(cur)
    
    logger.info('Getting shape info')
    shape_dict = get_shape_dict_from_postgres(cur, version_id)
    logger.info('%d total shapes' % (len(shape_dict)))

    logger.info('Getting info about trips without shapes')
    shapeless_st_dict = get_nonshapes_from_postgres(cur, version_id)
    logger.info('%d total trips without shapes' % (len(shapeless_st_dict)))

    logger.info('Creating new shapes')
    trip_shape_map = add_shapeless_trips_to_shape_dict(shape_dict,
                                                       shapeless_st_dict)
    unique_shapes = list(set(trip_shape_map.values()))
    logger.info('%d new unique shapes' % (len(unique_shapes)))

    logger.info('Adding shape_dist_traveled')
    shape_dict = add_shape_dist_traveled(shape_dict)

    logger.info('Removing redundant shape points')
    cleaned_shape_dict = remove_redundant_shape_points(shape_dict, eps_meter,
                                                       logger)

    print_clean_stats(shape_dict, cleaned_shape_dict, logger)

    temp_table_name = 'gtfs_shapes_fixed'
    logger.info('Inserting to temp table %s' % temp_table_name)
    create_temp_table(cur, temp_table_name)
    load_to_temp_table(cur, temp_table_name, cleaned_shape_dict, logger)

    logger.info('Deleting redundant shape points')
    delete_shape_points(cur, temp_table_name, version_id)
    logger.info('Updating shape_dist_traveled')
    update_shape_dist_traveled(cur, temp_table_name, version_id)
    logger.info('Inserting new shapes')
    insert_new_shapes(cur, temp_table_name, version_id)
    logger.info('Updating trips without shapes')
    update_shapeless_trips(cur, trip_shape_map, version_id)
    if version_id != 'temp':
        logger.info("Rewriting history.")
        rewrite_history(cur, temp_table_name, version_id)
    logger.info('Done updating gtfs_shapes!')

    logger.info('Reenabling triggers')
    enable_triggers(cur)


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
    parser.add_argument("--pg-host", dest="pg_host",
                        required=True,
                        metavar="POSTGRES_HOST",
                        help="URI for postgres")
    parser.add_argument("--pg-user", dest="pg_user",
                        required=True,
                        metavar="POSTGRES_USER",
                        help="username for postgres")
    parser.add_argument("--pg-pass", dest="pg_pass",
                        required=True,
                        metavar="POSTGRES_PASS",
                        help="password for postgres")
    parser.add_argument("--gtfs-version", dest="gtfs_version",
                        metavar="GTFS_VERSION",
                        help="version id of GTFS schedule to modify (most recent if none)")
    parser.add_argument("--write-mode", dest="write",
                        action='store_true',
                        help="run script in write mode (actually write to postgres)")
    parser.set_defaults(write=False)
    args = parser.parse_args()

    logger_name = '%s_shape_cleaner' % (args.agency)
    
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
        clean_shapes(cur, 2.0, logger, args.gtfs_version)
        if args.write:
            logger.warning('Committing changes.')
            conn.commit()
        else:
            logger.warning('Rolling back (debug mode).')
            conn.rollback()
    except:
        logger.exception("Failed to clean shapes!")
        conn.rollback()
    finally:
        conn.close()
