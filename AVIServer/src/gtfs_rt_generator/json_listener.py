'''
Created on Dec 7, 2016

@author: Jacob Lynn
'''
import time
from logging import getLogger
import json
import paho.mqtt.client as paho
from paho.mqtt.client import MQTTv31
import dateutil.parser
import datetime
import pytz
import gtfs_realtime_pb2
import psycopg2 as pg


def unix_time(dt):
    epoch = pytz.utc.localize(datetime.datetime.utcfromtimestamp(0))
    delta = dt - epoch
    return delta.total_seconds()


def unix_time_millis(dt):
    return unix_time(dt) * 1000.0


class JsonListener(object):
    def __init__(self, mqtt_host, mqtt_port, mqtt_user, mqtt_pass, agency,
                 pg_host, pg_user, pg_pass, logger_name):
        self.mc = None
        self.agency = agency

        self.logger = getLogger(logger_name)

        self.name = logger_name
        self.mqtt_host, self.mqtt_port = mqtt_host, mqtt_port
        self.mqtt_user, self.mqtt_pass = mqtt_user, mqtt_pass

        vals = agency, pg_host, pg_user, pg_pass
        self.conn_str = "dbname='%s' host='%s' user='%s' password='%s'" % vals

        self.retry_time = 30

        self.msg_count = 0


    def initialize(self):
        self.logger.info("Initializing from Postgres data")
        conn = pg.connect(self.conn_str)
        cur = conn.cursor()
        dt_now = pytz.utc.localize(datetime.datetime.utcnow())

        # Load recent events
        dt_old = dt_now - datetime.timedelta(minutes=10)
        ev_qry = """
        SELECT ts, vehicle_id, event_type, stop_id, stop_sequence,
        trip_id, route_id, delay
        FROM event
        WHERE ts > %s
        """
        cur.execute(ev_qry, (dt_old,))
        for res in cur:
            ev_dict = {'ts': res[0], 'vehicle_id': res[1],
                       'event_type': res[2], 'stop_id': res[3],
                       'stop_sequence': res[4], 'trip_id': res[5],
                       'route_id': res[6], 'delay': res[7]}
            orig_keys = ev_dict.keys()
            for key in orig_keys:
                if ev_dict[key] is None:
                    del ev_dict[key]
            self.process_event(ev_dict)

        # Load recent locations
        dt_old = dt_now - datetime.timedelta(minutes=2)
        pl_qry = """
        SELECT ts, vehicle_id, speed, bearing, accuracy,
        trip_id, route_id, latitude, longitude
        FROM projected_location
        WHERE ts > %s
        """
        cur.execute(pl_qry, (dt_old,))
        for res in cur:
            pl_dict = {'ts': res[0], 'vehicle_id': res[1],
                       'speed': res[2], 'bearing': res[3],
                       'accuracy': res[4], 'trip_id': res[5],
                       'route_id': res[6], 'latitude': res[7],
                       'longitude': res[8]}
            orig_keys = pl_dict.keys()
            for key in orig_keys:
                if pl_dict[key] is None:
                    del pl_dict[key]
            self.process_pl(pl_dict)

        cur.close()
        conn.close()

    def start(self):
        self.initialize()
        
        self.logger.info("JsonListener initialization...")
        self.connect_mqtt()


    def subscribe_all(self):
        topic_str = self.agency + "/json/event/+/+/+"
        self.subscribe(topic_str,qos=2)

        topic_str = self.agency + "/json/+/pl"
        self.subscribe(topic_str,qos=0)


    def on_trip_update(self, trip_update):
        self.logger.warn("Received TripUpdate -- ignoring!")
        self.logger.warn("Override on_trip_update to perform action.")
        self.logger.debug(trip_update)


    def on_vehicle_position(self, veh_pos):
        self.logger.warn("Received VehiclePosition -- ignoring!")
        self.logger.warn("Override on_vehicle_position to perform action.")
        self.logger.debug(veh_pos)

        
    def on_message(self, mosq, obj, msg):
        self.msg_count += 1
        if self.msg_count % 100 == 1:
            self.logger.info("Processing message #%d" % self.msg_count)

        self.logger.debug("Message received on topic " + msg.topic)
        try:
            msg_dict = json.loads(msg.payload)
            if 'pl' in msg.topic:
                self.process_pl(msg_dict)
            elif 'event' in msg.topic:
                self.process_event(msg_dict)

        except Exception:
            self.logger.exception("Error receiving/parsing MQTT JSON!")


    def process_event(self, ev_dict):
        # Convert event message (json format) into
        # GTFS-rt TripUpdate protobuf message.
        
        trip_update = gtfs_realtime_pb2.TripUpdate()
        
        trip_desc = trip_update.trip
        trip_desc.trip_id = ev_dict['trip_id']
        trip_desc.route_id = ev_dict['route_id']

        veh_desc = trip_update.vehicle
        veh_desc.id = ev_dict['vehicle_id']

        stu = trip_update.stop_time_update.add()
        stu.stop_sequence = ev_dict['stop_sequence']
        stu.stop_id = ev_dict['stop_id']

        if ev_dict['event_type'] == 'arrival':
            ste = stu.arrival
        else:
            ste = stu.departure
        ste.delay = int(ev_dict['delay']/1000)

        ts = ev_dict['ts']
        if isinstance(ts, basestring):
            ts = int(unix_time(dateutil.parser.parse(ts)))
        elif isinstance(ts, datetime.datetime):
            ts = int(unix_time(ts))
        ste.time = ts
        trip_update.timestamp = ts

        self.on_trip_update(trip_update)


    def process_pl(self, pl_dict):
        # Convert projected location (json format) into
        # GTFS-rt VehiclePosition protobuf message.

        veh_pos = gtfs_realtime_pb2.VehiclePosition()

        trip_desc = veh_pos.trip
        trip_desc.trip_id = pl_dict['trip_id']
        if 'route_id' in pl_dict:
            trip_desc.route_id = pl_dict['route_id']

        pos = veh_pos.position
        pos.latitude = pl_dict['latitude']
        pos.longitude = pl_dict['longitude']
        if 'speed' in pl_dict:
            pos.speed = pl_dict['speed']
        if 'bearing' in pl_dict:
            pos.bearing = pl_dict['bearing']
        if 'postmile' in pl_dict:
            pos.odometer = pl_dict['postmile']

        veh_desc = veh_pos.vehicle
        veh_desc.id = pl_dict['vehicle_id']

        ts = pl_dict['ts']
        if isinstance(ts, basestring):
            ts = int(unix_time(dateutil.parser.parse(ts)))
        elif isinstance(ts, datetime.datetime):
            ts = int(unix_time(ts))
        veh_pos.timestamp = ts

        self.on_vehicle_position(veh_pos)
        
    
    def connect_mqtt(self):
        self.mc = paho.Client(self.name, protocol=MQTTv31)
        self.mc.username_pw_set(self.mqtt_user, self.mqtt_pass)

        self.logger.info("Setting up callbacks...")
        self.mc.on_message = self.on_message
        self.mc.on_connect = self.on_connect

        self.logger.info("Actually attempting to connect...")
        try:
            self.mc.connect(host=self.mqtt_host, port=self.mqtt_port)
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
            self.subscribe_all()
        else:
            self.logger.error("!! Failed to connect to MQTT with error code " + str(rc))


    def subscribe(self, topic, qos=0):
        self.logger.info("Subscribing to topic at %s at qos %d..." % (topic, qos))
        result = self.mc.subscribe(topic, qos)
