import sys
sys.path.append('../../res')
import avi_messages_pb2 as avi_pb
import time
from logging import getLogger
import paho.mqtt.client as paho
from paho.mqtt.client import MQTTv31
import json
import dateutil.parser
import datetime
import pytz

def unix_time(dt):
    epoch = pytz.utc.localize(datetime.datetime.utcfromtimestamp(0))
    delta = dt - epoch
    return delta.total_seconds()

def unix_time_millis(dt):
    return unix_time(dt) * 1000.0

def bytes_to_json(mqtt_msg):
    return json.loads(mqtt_msg.payload)

def rl_json_to_pb(json_dict):
    rl_pb = avi_pb.RawLocationMessage()
    if 'device_id' in json_dict:
        rl_pb.device_id = json_dict['device_id']
    else:
        rl_pb.device_id = json_dict['vehicle_id']
        
    dt = dateutil.parser.parse(json_dict['ts'])
    rl_pb.ts = int(unix_time_millis(dt))
    
    if 'latitude' in json_dict:
        rl_pb.latitude = json_dict['latitude']
    if 'longitude' in json_dict:
        rl_pb.longitude = json_dict['longitude']
    if 'speed' in json_dict:
        rl_pb.speed = json_dict['speed']
    if 'bearing' in json_dict:
        rl_pb.bearing = json_dict['bearing']
    if 'accuracy' in json_dict:
        rl_pb.accuracy = json_dict['accuracy']
    return rl_pb

class OldJsonToNewPb:
    def __init__(self, host, user, pwd, agency, old_agency, logger_name):
        self.logger = getLogger(logger_name)
        
        self.agency, self.old_agency = agency, old_agency
        
        self.mc = None
        self.host = host
        self.user, self.pwd = user, pwd
        self.retry_time = 30
        self.msg_count = 0

    def start(self):
        self.logger.info("Initializing OldJsonToNewPb")
        self.connect_mqtt()

    def init_subs(self):
        # vehicle-based messages, in principle
        vms = ['raw_location']
        tl = [self.old_agency + '/json/+/' + m for m in vms]
        for t in tl:
            self.subscribe(t, qos=0)

    def get_push_topic(self, in_topic):
        topic = in_topic.replace("/json/","/pb/")
        topic = topic.replace("/raw_location", "/rl")
        return topic.replace(self.old_agency, self.agency)

    def on_message(self, mosq, obj, msg):
        self.msg_count += 1
        if self.msg_count % 100 == 1:
            self.logger.info("Receiving message # %d" % self.msg_count)
            
        self.logger.debug("Message received on topic " + msg.topic)
        try:
            rl_pb = rl_json_to_pb(json.loads(msg.payload))
            topic = self.get_push_topic(msg.topic)
            self.publish(topic, bytearray(rl_pb.SerializeToString()), 0)
        except Exception:
            self.logger.exception("Error receiving/parsing old JSON on MQTT!")

    def connect_mqtt(self):
        self.mc = paho.Client("", protocol=MQTTv31)
        self.mc.username_pw_set(self.user, self.pwd)

        self.logger.info("Setting up callbacks...")
        self.mc.on_message = self.on_message
        self.mc.on_disconnect = self.on_disconnect
        self.mc.on_connect = self.on_connect

        self.logger.info("Actually attempting to connect...")
        try:
            self.mc.connect(host=self.host, port=1883)
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

    def publish(self, topic, payload, qos=0):
        self.logger.debug("Publishing to topic %s at qos %d..." % (topic, qos))
        result = self.mc.publish(topic, payload, qos)
