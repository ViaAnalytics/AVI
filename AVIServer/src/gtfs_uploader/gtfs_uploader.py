import psycopg2 as pg
from psycopg2 import ProgrammingError, IntegrityError
import logging
import zipfile
import json
from StringIO import StringIO
import csv
from shape_cleaner import clean_shapes
from stop_shape_dist_traveled import populate_st_postmiles
from argparse import ArgumentParser

class GTFSUploader:
    def __init__(self, db_name, host, user, password, logger_name,
                 pkeys_file, debug):
        self.logger = logging.getLogger(logger_name)

        if debug:
            self.logger.warning("Running in debug mode! (Not actually writing to Postgres.)")

        self.debug = debug

        self.initialized = False
        
        self.pkey_dict = self._create_pkey_dict(pkeys_file)

        self.logger.info("Connecting to Postgres.")

        self.log_level = self.logger.getEffectiveLevel()

        vals = (db_name, host, user, password)
        self.conn_str = "dbname='%s' host='%s' user='%s' password='%s'" % vals

        # The order here is important. More basic tables get inserted
        # before tables that depend on them for foreign key relationships.
        tnames = ['agency', 'stops', 'routes', 'shapes', 'trips',
                  'stop_times', 'calendar', 'calendar_dates', 'fare_attributes',
                  'fare_rules', 'frequencies', 'transfers', 'feed_info']
        self.possible_table_names = tnames

        # Pare down to the list of GTFS tables actually in the zipfile.


    def initialize(self, gtfs_zip_file, version_id, desc_text):
        self.logger.info("Initializing GTFSUploader.")
        
        self.gtfs_zip_file = gtfs_zip_file
        self.version_id = version_id
        self.desc_text = desc_text

        self.table_names = self._get_tables()
        self.logger.info("The following tables are in the zipfile and " +
                         "will be loaded:")
        self.logger.info(self.table_names)
        self.conn = pg.connect(self.conn_str)
                
        self.initialized = True
        
        self.logger.info("Finished initializing GTFSUploader.\n")


    def _get_tables(self):
        root = zipfile.ZipFile(self.gtfs_zip_file, 'r')
        table_names = []
        for table_name in self.possible_table_names:
            try:
                file_name = table_name + ".txt"
                fi = root.open(file_name)
                table_names.append(table_name)
                fi.close()
            except KeyError as e:
                pass

        return table_names


    def _create_pkey_dict(self, pkeys_file):
        pkey_dict = None
        with open(pkeys_file, 'r') as pkeys:
            pkey_dict = json.load(pkeys)
        return pkey_dict
    

    def update_all(self):
        """
        Logically, this script should insert the new version without actually
        changing rows that haven't been changed relative to the old version.
        This requires some combination of update, insert, and delete.

        The basic principle to perform this is being borrowed from here:
        http://tapoueh.org/blog/2013/03/15-batch-update.

        We perform all of this in a single transaction so that we can
        associate the new version with a single timestamp. By default,
        psycopg2 implicitly continues the same transaction until commit() or
        rollback(). Transactions are begun upon the first execute() command on
        cursor.
        """

        if not self.initialized:
            raise Exception("GTFSUploader is uninitialized!")

        cur = self.conn.cursor()
        success = True
        fields_dict = {}
        # For each table, bulk load new data into temp table,
        # and get fields in the txt file.
        for table_name in self.table_names:
            try:
                self.logger.info("Bulk loading " + table_name + " table.")

                fields = self._load_into_temp_table(cur, table_name)
                fields_dict[table_name] = fields
            except Exception as e:
                self.logger.exception("Postgres error loading %s table." % (table_name))
                self.logger.error("Rolling back commit!")
                self.conn.rollback()
                success = False
                raise

        # Fix shapes and stop times in temp tables.
        if success:
            try:
                # cleaning parameter
                eps_meter = 2.0
                clean_shapes(cur, eps_meter, self.logger, version_id='temp')
                populate_st_postmiles(cur, self.logger, version_id='temp')

                if 'shape_dist_traveled' not in fields_dict['stop_times']:
                    fields_dict['stop_times'].append('shape_dist_traveled')
                if 'shapes' not in self.table_names:
                    # Insert shapes table into the table list before 'trips'.
                    # Needs to be before trips because of the 'good_shape_id'
                    # constraint.
                    trip_idx = self.table_names.index('trips')
                    self.table_names.insert(trip_idx, 'shapes')
                    fields_dict['shapes'] = ['shape_id', 'shape_pt_lat',
                                             'shape_pt_lon',
                                             'shape_pt_sequence',
                                             'shape_dist_traveled']
                if 'shape_dist_traveled' not in fields_dict['shapes']:
                    fields_dict['shapes'].append('shape_dist_traveled')
                if 'shape_id' not in fields_dict['trips']:
                    fields_dict['trips'].append('shape_id')

            except Exception as e:
                self.logger.exception("""Postgres error cleaning shapes or
                populating shape_dist_traveled.""")
                self.logger.error("Rolling back commit!")
                self.conn.rollback()
                success = False
                raise

        # Now update actual gtfs tables from temp tables.
        if success:
            for table_name in self.table_names:
                try:
                    fields = fields_dict[table_name]
                    self._update_table(cur, table_name, fields)
                except Exception as e:
                    self.logger.exception("Postgres error updating %s table."
                                          % (table_name))
                    self.logger.error("Rolling back commit!")
                    self.conn.rollback()
                    success = False
                    raise

        if success:
            try:
                self._update_version_table(cur)
                if not self.debug:
                    self.logger.error("Committing changes.")
                    self.conn.commit()
                else:
                    self.logger.error("Rolling back commit! (Debug mode)")
                    self.conn.rollback()
            except:
                self.logger.exception("Error setting version id.")
                self.logger.error("Rolling back commit!")
                self.conn.rollback()
                raise
        cur.close()


    def _update_version_table(self, cur):
        insert_lines = []
        insert_lines.append('INSERT INTO gtfs_version')
        insert_lines.append('(version_id, t_start, description)')
        insert_lines.append('VALUES (%s, now(), %s)')
        insert_stmt = '\n'.join(insert_lines)
        insert_vals = (self.version_id, self.desc_text)

        self.logger.info("Adding new version to gtfs_version")
        self.logger.debug("Executing insert statement: \n" +
                          cur.mogrify(insert_stmt, insert_vals))

        cur.execute(insert_stmt, insert_vals)


    def _spit_entire_table(self, cur, table, title_text):
        if self.log_level < logging.INFO:
            self.logger.debug("\n")
            self.logger.debug("=====================" + title_text + "======================")
            cur.execute('select * from ' + table)
            for res in cur:
                self.logger.debug(res)
            self.logger.debug("=====================================================\n")
        

    def _update_table(self, cur, table_name, fields):
        """
        For each GTFS table:
        1. Insert the new data into a temp batch table.
          -- EDIT -- now inserting in a separate function
        2. Update rows in the live table that are changed in the temp table.
        3. Insert rows into the live table that are new in the temp table.
        3. Delete rows from the live table that don't appear in the temp table.
        4. Drop the temp table.
        """
        
        actual_table_name = "gtfs_" + table_name
        temp_table_name = actual_table_name + "_temp"
        pkeys = self.pkey_dict[table_name]
        
        pkeys_set = set(pkeys)
        all_fields_set = set(fields)
        non_pkeys = list(all_fields_set - pkeys_set)

        if table_name == 'stops':
            # disable self-referential foreign key constraint
            disable_fk_stmt = """
            ALTER TABLE gtfs_stops
            DROP CONSTRAINT gtfs_stops_parent_station_fkey;
            """
            cur.execute(disable_fk_stmt)

        if len(non_pkeys) > 0:
            self.logger.info("Performing update of %s." % (table_name))
            self._step_1_update(cur, actual_table_name, temp_table_name, 
                                pkeys, non_pkeys)
        else:
            self.logger.info("No fields which are not primary keys in " + actual_table_name)
            self.logger.info("Skipping update.")

        self.logger.info("Performing delete.")
        self._step_3_delete(cur, actual_table_name, temp_table_name, 
                            pkeys, non_pkeys)
        
        self.logger.info("Performing insert.")
        self._step_2_insert(cur, actual_table_name, temp_table_name, 
                            pkeys, non_pkeys)
        if table_name == 'stops':
            # re-enable self-referential foreign key constraint
            enable_fk_stmt = """
            ALTER TABLE gtfs_stops
            ADD CONSTRAINT gtfs_stops_parent_station_fkey
            FOREIGN KEY (parent_station)
            REFERENCES gtfs_stops(stop_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
            """
            cur.execute(enable_fk_stmt)

        self.logger.info("Finished working on " + table_name + " table.\n")


    # Update fields that exist in both but have been modified.
    def _step_1_update(self, cur, act_table, tmp_table, pkeys, non_pkeys):
        update_stmt = self._generate_update_stmt(act_table, tmp_table,
                                                 pkeys, non_pkeys)

        self.logger.debug("Executing update statement: \n" + update_stmt)
        cur.execute(update_stmt)
        for res in cur:
            # Note that this count might be confusing because of
            # cascading deletes after rows from other tables were removed.
            self.logger.info("%d rows updated" % res[0])

        self._spit_entire_table(cur, act_table, "AFTER UPDATE")


    # Insert rows that exist in the temp table but not the real table.
    def _step_2_insert(self, cur, act_table, tmp_table, pkeys, non_pkeys):
        insert_stmt = self._generate_insert_stmt(act_table,
                                                 tmp_table,
                                                 pkeys, non_pkeys)
        self.logger.debug("Executing insert statement: \n" + insert_stmt)

        cur.execute(insert_stmt)
        for res in cur:
            # Note that this count might be confusing because of
            # cascading deletes after rows from other tables were removed.
            self.logger.info("%d rows inserted" % res[0])

        self._spit_entire_table(cur, act_table, "AFTER INSERT")


    # Delete rows from the real table that don't exist in the temp table
    def _step_3_delete(self, cur, act_table, tmp_table, pkeys, non_pkeys):
        del_stmt = self._generate_del_stmt(act_table,
                                           tmp_table,
                                           pkeys, non_pkeys)
        self.logger.debug("Executing delete statement: \n" + del_stmt)
        cur.execute(del_stmt)
        for res in cur:
            # Note that this count might be confusing because of
            # cascading deletes after rows from other tables were removed.
            self.logger.info("%d rows deleted" % res[0])

        self._spit_entire_table(cur, act_table, "AFTER DELETE")


    def _load_into_temp_table(self, cur, table_name):
        root = zipfile.ZipFile(self.gtfs_zip_file, 'r')
        file_name = table_name + ".txt"
        actual_table_name = "gtfs_" + table_name
        temp_table_name = actual_table_name + "_temp"
        
        # get the list of fields
        fi = root.open(file_name)
        first_row = fi.readline().rstrip()
        if first_row and first_row.startswith('\xef\xbb\xbf'):
            first_row = first_row[3:]
        fields = first_row.split(',')
        fi.close()
        # strip leading and trailing quotations in field names
        fields = [field.strip('"\'') for field in fields]

        # create temp table
        create_tmp_table_stmt = 'CREATE TEMP TABLE ' + temp_table_name + \
                                '(LIKE ' + actual_table_name + \
                                ' INCLUDING DEFAULTS INCLUDING INDEXES) ' + \
                                'ON COMMIT DROP'
        
        self.logger.debug("Executing create table statement: \n" + create_tmp_table_stmt)
        cur.execute(create_tmp_table_stmt)

        self.logger.info("Cleaning input file " + file_name)

        # get set of possible fields
        cur.execute('SELECT * FROM ' + actual_table_name + ' LIMIT 0')
        possible_fields = [desc[0] for desc in cur.description]
        self.logger.debug("Possible columns for table " + actual_table_name)
        self.logger.debug(possible_fields)

        actual_fields = [field for field in fields if field in possible_fields]
        self.logger.debug("Actual fields:")
        self.logger.debug(actual_fields)

        # 1. Change delimiter to handle edge cases. (!! This will break if a GTFS
        # file contains the new_delim character. !!)
        # 2. Don't copy fields in the zipfile that aren't in GTFS spec.
        new_delim = '^'
        fi = root.open(file_name)
        fi_new = StringIO()
        writer = csv.DictWriter(fi_new, delimiter=new_delim, fieldnames=tuple(actual_fields))
        reader = csv.DictReader(fi, fieldnames=tuple(fields))
        strip_reader = (
            dict((k, v.strip()) for k, v in row.items() if (v and (k in actual_fields)))
            for row in reader)

        writer.writerows(strip_reader)
        fi.close()

        fi_new.seek(0)
        fi_new.readline()

        # upload the data into temp table
        self.logger.info("Inserting new data into temporary table %s."
                         % (temp_table_name))
        cur.copy_from(fi_new, temp_table_name, sep=new_delim,
                      columns=tuple(actual_fields), null='')

        # Manually handle two edge cases. GTFS spec allows empty agency_id
        # field, if there's only one agency in the feed, but this makes
        # various joins annoying. If agency_id is null, set it equal to the
        # empty string.
        if table_name == 'agency':
            self.logger.info("Fixing null agency id in agency temp table.")
            fix_agency_stmt = self._generate_fix_agency_id_stmt(temp_table_name,
                                                                '')
            self.logger.debug("Executing fix statement:\n" + fix_agency_stmt)
            cur.execute(fix_agency_stmt)
            self._agency_list = self._get_list_of_agency_ids(cur)
            self.logger.info("List of agencies: ")
            self.logger.info(self._agency_list)

        if table_name == 'routes':
            self.logger.info("Fixing null agency id in routes temp table.")
            if len(self._agency_list)==1:
                fix_routes_stmt = self._generate_fix_agency_id_stmt(temp_table_name,
                                                                    self._agency_list[0])
                self.logger.debug("Executing fix statement:\n" + fix_routes_stmt)
                cur.execute(fix_routes_stmt)
            else:
                if self._null_agency_ids_with_multiple_agencies(cur):
                    raise Exception("""Multiple agencies in this feed, but there
                    are null agency ids in routes.txt.""")

        self._spit_entire_table(cur, temp_table_name, "TEMP TABLE")

        return actual_fields


    def _get_list_of_agency_ids(self, cur):
        query = "SELECT DISTINCT(agency_id) FROM gtfs_agency_temp"
        cur.execute(query)
        agency_list = []
        for res in cur:
            agency_list.append(res[0])
        return agency_list


    def _null_agency_ids_with_multiple_agencies(self, cur):
        if len(self._agency_list)==1:
            return False
        elif len(self._agency_list)>1:
            query = "SELECT COUNT(*) FROM gtfs_routes_temp WHERE agency_id IS NULL"
            cur.execute(query)
            for res in cur:
                if res[0]>0:
                    return True
        else:
            raise Exception("No agency ids.")
            
        

    # If agency_id is null, set it to empty string. (VIA convention.)
    def _generate_fix_agency_id_stmt(self, tbl, target_agency):
        fix_lines = []
        fix_lines.append('UPDATE ' + tbl)
        fix_lines.append('SET agency_id=\'' + target_agency + '\' WHERE agency_id IS NULL')
        return '\n'.join(fix_lines)
    

    # This statement deletes rows from the actual table that don't appear
    # in the temp table.
    def _generate_del_stmt(self, actual_table_name, temp_table_name,
                           pkeys, non_pkeys):
        del_lines = []        
        del_lines.append('WITH u AS (')
        del_lines.append('DELETE FROM ' + actual_table_name + ' t')
        del_lines.append('WHERE NOT EXISTS (')
        del_lines.append('SELECT 1 FROM ' + temp_table_name + ' s')
        del_lines.append('WHERE ' + self._generate_matched_pkeys_stmt('s', 't', pkeys))
        del_lines.append(')')
        del_lines.append('RETURNING *)')
        del_lines.append('SELECT count(*) FROM u')
        
        return '\n'.join(del_lines)


    # This statement updates rows in the actual table that appear in the
    # temp table, but only when they are actually different.
    def _generate_update_stmt(self, actual_table_name, temp_table_name,
                              pkeys, non_pkeys):
        update_lines = []
        update_lines.append('WITH u AS (')
        update_lines.append('UPDATE ' + actual_table_name + ' t')
        update_lines.append(self._generate_set_stmt(non_pkeys))
        update_lines.append('FROM ' + temp_table_name + ' s')
        update_lines.append('WHERE ' + self._generate_matched_pkeys_stmt('s', 't', pkeys))
        update_lines.append('AND (' + self._generate_unmatched_nonpkeys_stmt('s', 't', non_pkeys) + ')')
        update_lines.append('RETURNING *)')
        update_lines.append('SELECT count(*) from u')

        return '\n'.join(update_lines)


    # This statement inserts rows into the actual table that appear only
    # in the temp table.
    def _generate_insert_stmt(self, actual_table_name, temp_table_name,
                              pkeys, non_pkeys):
        all_fields = pkeys+non_pkeys
        all_select_str = ', '.join(['s.'+field for field in all_fields])
        all_insert_str = ', '.join(pkeys + non_pkeys)

        insert_lines = []
        insert_lines.append('WITH u AS (')
        insert_lines.append('INSERT INTO ' + actual_table_name + '(' + all_insert_str + ')')
        insert_lines.append('SELECT ' + all_select_str)
        insert_lines.append('FROM ' + temp_table_name + ' s LEFT JOIN ' + actual_table_name + ' t')
        insert_lines.append(self._generate_pkeys_on_stmt('s', 't', pkeys))
        insert_lines.append('WHERE ' + self._generate_null_pkeys_stmt('t', pkeys))
        insert_lines.append('RETURNING *)')
        insert_lines.append('SELECT count(*) FROM u')

        return '\n'.join(insert_lines)


    def _generate_pkeys_on_stmt(self, t1, t2, pkeys):
        on_els = []
        for pkey in pkeys:
            on_els.append(t1 + '.' + pkey + ' = ' + t2 + '.' + pkey)
        return 'ON ' + ' AND '.join(on_els)


    def _generate_set_stmt(self, non_pkeys):
        set_els = []
        for field in non_pkeys:
            set_els.append(field + ' = s.' + field)
        return 'SET ' + ', '.join(set_els)


    def _generate_matched_pkeys_stmt(self, t1, t2, pkeys):
        where_els = []
        for pkey in pkeys:
            where_els.append(t1 + '.' + pkey + ' = ' + t2 + '.' + pkey)
        return ' AND '.join(where_els)


    def _generate_unmatched_nonpkeys_stmt(self, t1, t2, non_pkeys):
        where_els = []
        for field in non_pkeys:
            t1f = t1+'.'+field
            t2f = t2+'.'+field
            where_el = '(' + t1f + ' != ' + t2f + \
                       ' OR (' + t1f + ' IS NULL AND ' + t2f + ' IS NOT NULL)' + \
                       ' OR (' + t1f + ' IS NOT NULL AND ' + t2f + ' IS NULL))'
            where_els.append(where_el)
        return ' OR '.join(where_els)


    def _generate_null_pkeys_stmt(self, table, pkeys):
        where_els = []
        for key in pkeys:
            where_els.append(table + '.' + key + ' IS NULL')
        return ' AND '.join(where_els)
            


if __name__ == "__main__":
    parser = ArgumentParser()

    parser.add_argument("-a", "--agency-name", dest="agency", 
                        metavar="AGENCY",
                        required=True,
                        help="name of agency")
    parser.add_argument("-l", "--log-file", dest="logfile", 
                        metavar="LOGFILE",
                        default=None,
                        help="file for log messages")
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
    parser.add_argument("--gtfs-zip-file", dest="gtfs_zip_file",
                        required=True,
                        metavar="GTFS_ZIP_FILE",
                        help="full or relative path to zip file containing GTFS schedule info")
    parser.add_argument("--pkeys-file", dest="pkeys_file",
                        required=True,
                        metavar="PKEYS_FILE",
                        help="full or relative path to JSON file containing effective GTFS primary keys")
    parser.add_argument("--write-mode", dest="write",
                        action='store_true',
                        help="run script in write mode (actually write to postgres)")
    parser.set_defaults(write=False)
    parser.add_argument("--gtfs-version", dest="gtfs_version",
                        required=True,
                        metavar="GTFS_VERSION",
                        help="arbitrary identifier for this GTFS version")
    parser.add_argument("--gtfs-desc", dest="gtfs_desc",
                        metavar="GTFS_DESCRIPTION",
                        default="",
                        help="Unstructured text describing this version")


    args = parser.parse_args()

    logger_name = '%s_gtfs_uploader' % (args.agency)
    
    logger = logging.getLogger(logger_name)

    loglevel = getattr(logging, args.loglevel)
    logfile = args.logfile

    if logfile is not None:
        logging.basicConfig(format='%(asctime)s %(message)s',
                            filename=logfile,
                            level=loglevel)
    else:
        logging.basicConfig(format='%(asctime)s %(message)s',
                            level=loglevel)
        
    uploader = GTFSUploader(args.agency, args.pg_host, args.pg_user,
                            args.pg_pass, logger_name,
                            args.pkeys_file, not args.write)

    uploader.initialize(args.gtfs_zip_file, args.gtfs_version, args.gtfs_desc)

    uploader.update_all()
    
