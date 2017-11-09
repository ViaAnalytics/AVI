import time
from logging import getLogger
import paho.mqtt.client as paho
from paho.mqtt.client import MQTTv31
import json
import dateutil.parser
import datetime
import pytz
import psycopg2 as pg

class RawlocTripAssigner:
    def __init__(self, mqtt_host, mqtt_user, mqtt_pass, agency, old_agency,
                 pg_host, pg_user, pg_pass, tz, zero_hour, logger_name):
        self.logger = getLogger(logger_name)
        
        self.agency, self.old_agency = agency, old_agency
        self.tz, self.zero_hour = tz, zero_hour
        
        self.mc = None
        self.mqtt_host = mqtt_host
        self.mqtt_user, self.mqtt_pass = mqtt_user, mqtt_pass
        self.retry_time = 30
        self.msg_count = 0

        vals = (agency, pg_host, pg_user, pg_pass)
        self.conn_str = "dbname='%s' host='%s' user='%s' password='%s'" % vals

        self.tas = {}
        self.blocks = {}
        self.trips = {}
        self.devices = []

    def start(self):
        self.logger.info("Initializing RawlocTripAssigner")
        self.conn = pg.connect(self.conn_str)

        # load various useful data elements
        self.load_blocks()
        self.load_devices()
        self.load_assignments()

        # start listening for raw locations
        self.connect_mqtt()

    def init_subs(self):
        # vehicle-based messages, in principle
        vms = ['raw_location']
        tl = [self.old_agency + '/json/+/' + m for m in vms]
        for t in tl:
            self.subscribe(t, qos=0)

    def load_blocks(self):
        qry = "SELECT trip_id, block_id || '_' || service_id FROM gtfs_trips"
        cur = self.conn.cursor()
        cur.execute(qry)
        for el in cur:
            tid, bid = el[0], el[1]
            if tid is None or bid is None:
                continue
            # trip -> block association
            self.blocks[tid] = bid

            # block -> trips association
            if bid not in self.trips:
                self.trips[bid] = []
            self.trips[bid].append(tid)

        tids = set([tid for tid in self.blocks])
        bids = set([self.blocks[tid] for tid in self.blocks])
        self.logger.info("%d total blocks loaded" % len(bids))
        self.logger.info("%d total trips loaded" % len(tids))
        cur.close()

    def load_devices(self):
        qry = "SELECT id FROM vehicle"
        cur = self.conn.cursor()
        cur.execute(qry)
        for el in cur:
            self.devices.append(el[0])
        self.logger.info("%d total devices loaded" % len(self.devices))
        cur.close()

    def load_assignments(self):
        ts_now = pytz.utc.localize(datetime.datetime.utcnow())
        sd = self.get_active_date(ts_now)
        sd_yesterday = sd + datetime.timedelta(days=-1)
        sd_tmrw = sd + datetime.timedelta(days=1)
        sds = (sd_yesterday, sd, sd_tmrw)
        qry = """SELECT trip_id, active_date, vehicle_id
        FROM trip_assignment
        WHERE active_date in %s"""
        cur = self.conn.cursor()
        cur.execute(qry, (sds,))
        n_ass = 0
        for el in cur:
            tid, sd, vid = el[0], el[1], el[2]
            bid = self.get_block_id_for_trip_id(tid)
            if sd not in self.tas:
                self.tas[sd] = {}
            self.tas[sd][bid] = vid
            n_ass += 1
        cur.close()
        str_dates = tuple(map(str, sds))
        self.logger.info("Assignment dates: %s, %s, %s" % str_dates)
        self.logger.info("Number of assignments loaded: %d" % n_ass)

    def get_active_date(self, ts):
        new_ts = ts.astimezone(self.tz)
        new_ts = new_ts - datetime.timedelta(hours=self.zero_hour)
        return new_ts.date()

    def get_block_id_for_trip_id(self, trip_id):
        if trip_id in self.blocks:
            return self.blocks[trip_id]

    def get_trip_ids_for_block_id(self, block_id):
        if block_id in self.trips:
            return self.trips[block_id]

    def insert_vehicle(self, vehicle_id):
        qry = """INSERT INTO device (id) VALUES ('%s');
        INSERT INTO vehicle (id, device_id) VALUES ('%s', '%s');
        """ % tuple([vehicle_id]*3)
        self.logger.debug("inserting vehicle:")
        self.logger.debug(qry)
        cur = self.conn.cursor()
        cur.execute(qry)
        self.conn.commit()
        cur.close()
        self.devices.append(vehicle_id)

    def delete_old_assignments(self, active_date, trip_ids, vehicle_id):
        qry = """DELETE FROM trip_assignment
        WHERE active_date = %s
        AND vehicle_id = %s
        AND trip_id in %s
        """
        qry_params = (active_date, vehicle_id, tuple(trip_ids))
        cur = self.conn.cursor()
        self.logger.debug("deleting old assignments:")
        self.logger.debug(cur.mogrify(qry, qry_params))
        cur.execute(qry, qry_params)
        self.conn.commit()
        cur.close()

    def insert_new_assignments(self, active_date, trip_ids, vehicle_id):
        vals_list = []
        for tid in trip_ids:
            vals = (tid, active_date, vehicle_id)
            vals_list.append(vals)
        cur = self.conn.cursor()
        qry = """INSERT INTO trip_assignment (trip_id, active_date, vehicle_id)
        VALUES """
        args_str = ','.join(cur.mogrify("(%s,%s,%s)", vals) for vals in vals_list)
        qry += args_str
        self.logger.debug("inserting assignment:")
        self.logger.debug(qry)
        cur.execute(qry)
        self.conn.commit()
        cur.close()

    def update_tas_if_necessary(self, rl_dict):
        if 'trip_id' not in rl_dict:
            # No trip information -- can't do anything
            return
        vid = rl_dict['vehicle_id']
        ts = dateutil.parser.parse(rl_dict['ts'])
        sd = self.get_active_date(ts)
        bid = self.get_block_id_for_trip_id(rl_dict['trip_id'])
        if sd not in self.tas:
            self.tas[sd] = {}
        if bid in self.tas[sd] and self.tas[sd][bid] == vid:
            # No need to modify -- already have this block assignment
            return

        if vid not in self.devices:
            self.insert_vehicle(vid)

        tids = self.get_trip_ids_for_block_id(bid)
        if bid in self.tas[sd]:
            # Delete old block assignment.
            self.delete_old_assignments(sd, tids, self.tas[sd][bid])
        # Add new block assignment.
        self.tas[sd][bid] = vid
        self.insert_new_assignments(sd, tids, vid)

    def on_message(self, mosq, obj, msg):
        self.msg_count += 1
        if self.msg_count % 100 == 1:
            self.logger.info("Receiving message # %d" % self.msg_count)
            
        self.logger.debug("Message received on topic " + msg.topic)
        try:
            rl_dict = json.loads(msg.payload)
            self.update_tas_if_necessary(rl_dict)
        except Exception:
            self.logger.exception("Error receiving/parsing JSON on MQTT!")

    def connect_mqtt(self):
        self.mc = paho.Client("", protocol=MQTTv31)
        self.mc.username_pw_set(self.mqtt_user, self.mqtt_pass)

        self.logger.info("Setting up callbacks...")
        self.mc.on_message = self.on_message
        self.mc.on_disconnect = self.on_disconnect
        self.mc.on_connect = self.on_connect

        self.logger.info("Actually attempting to connect...")
        try:
            self.mc.connect(host=self.mqtt_host, port=1883)
        except Exception as e:
            self.logger.exception("Failed to reconnect")
            self.logger.warn("Attempting to reconnect in 30 seconds...")
            time.sleep(self.retry_time)
            self.connect_mqtt()
            return

        self.logger.info("Finished initialization...")

        self.mc.loop_start()

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.logger.info("Successfully connected to MQTT. Setting up subscriptions.")
            self.init_subs()
        else:
            self.logger.error("!! Failed to connect to MQTT with error code " + str(rc))
            
    def on_disconnect(self, client, userdata, rc):
        if rc == 0:
            self.logger.info("Disconnected from MQTT broker normally")
        else:
            self.logger.error("!! Disconnected from MQTT with error code " + str(rc))

    def subscribe(self, topic, qos=0):
        self.logger.info("Subscribing to topic %s at qos %d..." % (topic, qos))
        result = self.mc.subscribe(topic, qos)
