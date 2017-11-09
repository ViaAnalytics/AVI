
-- important indices for each table (and history table)
CREATE INDEX gtfs_stops_parent_station ON gtfs_stops(parent_station);

CREATE INDEX gtfs_trips_route_id ON gtfs_trips(route_id);
CREATE INDEX gtfs_trips_history_route_id ON gtfs_trips_history(route_id);

CREATE INDEX gtfs_shapes_shape_id ON gtfs_shapes(shape_id);
CREATE INDEX gtfs_shapes_history_shape_id ON gtfs_shapes_history(shape_id);

CREATE INDEX gtfs_stop_times_trip_id ON gtfs_stop_times(trip_id);
CREATE INDEX gtfs_stop_times_stop_id ON gtfs_stop_times(stop_id);
CREATE INDEX gtfs_stop_times_history_trip_id ON gtfs_stop_times_history(trip_id);
CREATE INDEX gtfs_stop_times_history_stop_id ON gtfs_stop_times_history(stop_id);

CREATE INDEX gtfs_fare_rules_fare_id ON gtfs_fare_rules(fare_id);
CREATE INDEX gtfs_fare_rules_route_id ON gtfs_fare_rules(route_id);
CREATE INDEX gtfs_fare_rules_history_fare_id ON gtfs_fare_rules_history(fare_id);
CREATE INDEX gtfs_fare_rules_history_route_id ON gtfs_fare_rules_history(route_id);

CREATE INDEX gtfs_frequencies_trip_id ON gtfs_frequencies(trip_id);
CREATE INDEX gtfs_frequencies_history_trip_id ON gtfs_frequencies_history(trip_id);

CREATE INDEX gtfs_transfers_from_stop_id ON gtfs_transfers(from_stop_id);
CREATE INDEX gtfs_transfers_to_stop_id ON gtfs_transfers(to_stop_id);
CREATE INDEX gtfs_transfers_history_from_stop_id ON gtfs_transfers_history(from_stop_id);
CREATE INDEX gtfs_transfers_history_to_stop_id ON gtfs_transfers_history(to_stop_id);
