import gtfs_realtime_pb2
import time
import logging
import os
import threading
import copy

class FeedAggregator(object):
    def __init__(self, feed_dir, max_age, logger_name):
        self.feed_dir = feed_dir
        self.max_age = max_age
        
        self.trip_updates = {}
        self.vehicle_positions = {}
        self.entities = {}
        self.feed = None
        
        self.logger = logging.getLogger(logger_name)

        self.raw_filename = "gtfs_rt.pb"
        self.plaintext_fname = "gtfs_rt.txt"


    def update(self):
        self.logger.debug("Clearing old elements from feed.")
        self.clear_old_entities()

        # generate feed
        self.logger.debug("Generating feed.")
        feed = self.generate_feed()
        self.logger.debug(feed)

        # output feed to file
        if feed != self.feed:
            self.logger.debug("Writing feed to file.")
            self.write_feed(feed)
            self.feed = feed
        else:
            self.logger.debug("No changes -- not writing")

        
    def new_trip_update(self, trip_update):
        # trip_update should be a TripUpdate object from the
        # gtfs_realtime_pb2 class
        if not self.validate_ts(trip_update.timestamp):
            self.logger.debug("TripUpdate too old -- not storing")
            return
        
        tid = trip_update.trip.trip_id
        if tid in self.trip_updates:
            if self.trip_updates[tid].timestamp >= trip_update.timestamp:
                self.logger.debug("New TripUpdate older than pre-existing")
                return

        self.trip_updates[tid] = trip_update

        
    def new_vehicle_position(self, veh_pos):
        # veh_pos should be a VehiclePosition object from the
        # gtfs_realtime_pb2 class
        if not self.validate_ts(veh_pos.timestamp):
            self.logger.debug("VehiclePosition too old -- not storing")
            return
        
        vid = veh_pos.vehicle.id
        if vid in self.vehicle_positions:
            if self.vehicle_positions[vid].timestamp >= veh_pos.timestamp:
                self.logger.debug("New VehiclePosition older than pre-existing")
                return

        self.vehicle_positions[vid] = veh_pos

        
    def clear_old_entities(self):
        # This behavior is potentially thread unsafe.
        self.logger.debug("Clearing old entities.")
        
        ts = time.time()
        
        tid_list = copy.deepcopy(self.trip_updates.keys())
        vid_list = copy.deepcopy(self.vehicle_positions.keys())
        
        for tid in tid_list:
            if tid not in self.trip_updates:
                continue
            if not self.validate_ts(self.trip_updates[tid].timestamp, ts):
                del self.trip_updates[tid]
        for vid in vid_list:
            if vid not in self.vehicle_positions:
                continue
            if not self.validate_ts(self.vehicle_positions[vid].timestamp, ts):
                del self.vehicle_positions[vid]

                
    def validate_ts(self, ts, t_now=None):
        if t_now is None:
            t_now = time.time()
        return t_now - ts < self.max_age


    def generate_feed(self):
        feed = gtfs_realtime_pb2.FeedMessage()
        feed_header = feed.header
        feed_header.gtfs_realtime_version = "1.0"
        ts = 0

        trip_updates = copy.deepcopy(self.trip_updates)
        vehicle_positions = copy.deepcopy(self.vehicle_positions)

        for trip_id in trip_updates:
            trip_update_orig = trip_updates[trip_id]
            entity = feed.entity.add()
            entity.id = trip_id
            new_trip_update = entity.trip_update
            new_trip_update.CopyFrom(trip_update_orig)
            if new_trip_update.timestamp > ts:
                ts = new_trip_update.timestamp

        for veh_id in vehicle_positions:
            veh_pos_orig = vehicle_positions[veh_id]
            entity = feed.entity.add()
            entity.id = veh_id
            new_veh_pos = entity.vehicle
            new_veh_pos.CopyFrom(veh_pos_orig)
            if new_veh_pos.timestamp > ts:
                ts = new_veh_pos.timestamp

        feed_header.timestamp = ts
            
        return feed

    
    def write_feed(self, feed):
        raw_filename = os.path.join(self.feed_dir, self.raw_filename)
        
        raw_file = open(raw_filename, 'wb')
        raw_file.write(feed.SerializeToString())
        raw_file.close()

        plaintext_fname = os.path.join(self.feed_dir, self.plaintext_fname)
        
        plaintext_file = open(plaintext_fname, 'wb')
        plaintext_file.write(feed.__str__())
        plaintext_file.close()
