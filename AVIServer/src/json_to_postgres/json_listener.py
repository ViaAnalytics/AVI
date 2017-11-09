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


def unix_time(dt):
    epoch = pytz.utc.localize(datetime.datetime.utcfromtimestamp(0))
    delta = dt - epoch
    return delta.total_seconds()


def unix_time_millis(dt):
    return unix_time(dt) * 1000.0


def add_delay(json_msg, mtype):
    t_now = int(time.time()*1000)
    if 'ts' not in json_msg:
        return json_msg

    delta = t_now - unix_time_millis(dateutil.parser.parse(json_msg['ts']))
    json_msg[mtype.dt_el] = delta
    return json_msg


class MessageType(object):
    def __init__(self, token, qos, tbl=None):
        self.token = token
        self.qos, self.tbl = qos, tbl

        if tbl is None:
            self.tbl = token
        self.dt_el = self.tbl + '_dt'


class JsonListener(object):
    def __init__(self, host, port, username, password, agency, logger_name):
        self.mc = None
        self.agency = agency

        self.logger = getLogger(logger_name)

        self.name = logger_name
        self.host = host
        self.port = port
        self.username = username
        self.password = password

        self.retry_time = 30

        self.queue = None
        self.mtypes = [MessageType('rl', 0, 'raw_location'),
                       MessageType('exist', 2),
                       MessageType('pl', 0, 'projected_location'),
                       MessageType('event', 2)]

        self.msg_count = 0


    def start(self):
        if self.queue is None:
            self.logger.error("!! Queue not set! Not starting JsonListener.")
            return
        self.logger.info("JsonListener initialization...")
        self.connect_mqtt()


    def set_queue(self, queue):
        self.queue = queue
        

    def get_sub_topic(self, mtype):
        if mtype.token == 'event':
            # special topic structure for event topics
            return self.agency + "/json/event/+/+/+"
        else:
            return self.agency + '/json/+/' + mtype.token


    def get_mtype(self, msg):
        topic_els = msg.topic.split("/")
        for mtype in self.mtypes:
            if mtype.token in topic_els:
                return mtype
        return None


    def subscribe_all(self):
        for mtype in self.mtypes:
            topic = self.get_sub_topic(mtype)
            self.subscribe(topic, mtype.qos)

        
    def on_message(self, mosq, obj, msg):
        self.msg_count += 1
        if self.msg_count % 100 == 1:
            self.logger.info("Processing message #%d" % self.msg_count)

        self.logger.debug("Message received on topic " + msg.topic)
        try:
            mtype = self.get_mtype(msg)
            if mtype is None:
                self.logger.warn("Unrecognized message type!")
                return
            # add delay to message before pushing to Postgres
            json_msg = add_delay(json.loads(msg.payload), mtype)
            self.queue.enqueue(json_msg, mtype.tbl)
            self.logger.debug("Message added to queue " + mtype.tbl)

        except Exception:
            self.logger.exception("Error receiving/parsing MQTT JSON!")
        
    
    def connect_mqtt(self):
        self.mc = paho.Client(self.name, protocol=MQTTv31)
        self.mc.username_pw_set(self.username,self.password)

        self.logger.info("Setting up callbacks...")
        self.mc.on_message = self.on_message
        self.mc.on_connect = self.on_connect

        self.logger.info("Actually attempting to connect...")
        try:
            self.mc.connect(host=self.host, port=self.port)
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
