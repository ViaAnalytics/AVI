'''
Created on Dec 7, 2016

@author: Jacob Lynn
'''
import time
from logging import getLogger
import paho.mqtt.client as paho
from paho.mqtt.client import MQTTv31
import json
from protobuf_to_dict import protobuf_to_dict
from datetime import datetime
import threading
import pytz
import sys
sys.path.append('../../res')
import avi_messages_pb2 as msgs

def get_pub_topic(msg):
    return msg.topic.replace("/pb/","/json/")


def get_jsonstr_from_pb(pb_msg):
    json_dict = protobuf_to_dict(pb_msg, use_enum_labels=True)
    json_dict = clean_json(json_dict)
    return json.dumps(json_dict)

time_vars = ['ts', 'sent_time', 'last_gps_time', 'detect_time', 'expire_time']

def clean_json(json_dict):
    for key in json_dict:
        if key in time_vars:
            # All protobuf times are in epoch millis. Convert to ISO 8601
            # string.
            utc_dt = datetime.utcfromtimestamp(json_dict[key]/1000.)
            json_dict[key] = pytz.utc.localize(utc_dt).isoformat()

    return json_dict


class MessageType(object):
    def __init__(self, token, pb_cls, qos=0, retain=False):
        self.token, self.pb_class = token, pb_cls
        self.qos, self.retain = qos, retain

    def get_pb_msg(self):
        return self.pb_class()


class ProtobufConverter(object):
    def __init__(self, host, port, username, password, agency, logger_name):
        self.mc = None
        self.agency = agency

        self.logger = getLogger(logger_name)

        self.name = logger_name
        self.host = host
        self.port = port
        self.username = username
        self.password = password

        self.retry_time = 30.

        self.logger.info("ProtobufConverter initialization...")

        self.mtypes = [MessageType('rl', msgs.RawLocationMessage),
                       MessageType('exist', msgs.ExistMessage, 2, True)]

        self.msg_count = 0
        

    def get_sub_topic(self, mtype):
        return self.agency + '/pb/+/' + mtype.token


    def get_mtype(self, msg):
        topic_els = msg.topic.split("/")
        for mtype in self.mtypes:
            if mtype.token in topic_els:
                return mtype
        return None


    def get_pb_from_bytes(self, msg, mtype):
        pb_msg = mtype.get_pb_msg()
        pb_msg.ParseFromString(msg.payload)
        return pb_msg


    def subscribe_all(self):
        for mtype in self.mtypes:
            self.subscribe(self.get_sub_topic(mtype), qos=mtype.qos)


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
            topic = get_pub_topic(msg)
            pb_msg = self.get_pb_from_bytes(msg, mtype)
            json_str = get_jsonstr_from_pb(pb_msg)
            self.mc.publish(topic = topic, payload = json_str,
                            qos = mtype.qos, retain = mtype.retain)
            self.logger.debug("Message sent on topic " + topic)
            self.logger.debug(json_str)

        except Exception:
            self.logger.exception("Error receiving/parsing MQTT protobuf!")


    def on_publish(self, mosq, obj, mid):
        self.logger.info("Message " + str(mid) + " published.")
            

    def on_disconnect(self, client, userdata, rc):
        if rc == 0:
            self.logger.info("Successfully disconnected from MQTT.")
        else:
            self.logger.error("!! Improper disconnect from MQTT with error code " + str(rc))
            

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.logger.info("Successfully connected to MQTT. Setting up subscriptions.")
            self.subscribe_all()
        else:
            self.logger.error("!! Failed to connect to MQTT with error code " + str(rc))


    def start(self):
        self.logger.info("ProtobufConverter initialization...")
        self.connect_mqtt()


    def connect_mqtt(self):
        self.mc = paho.Client(self.name, protocol=MQTTv31)
        self.mc.username_pw_set(self.username,self.password)

        self.logger.info("Setting up callbacks...")
        self.mc.on_message = self.on_message
        self.mc.on_disconnect = self.on_disconnect
        self.mc.on_connect = self.on_connect

        self.logger.warn("Attempting to connect...")
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
        

    def subscribe(self, topic, qos=0):
        self.logger.info("Subscribing to topic at %s at qos %d..." % (topic, qos))
        result = self.mc.subscribe(topic, qos)
