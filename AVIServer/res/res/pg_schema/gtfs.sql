------------------------------------------------------------------------
--		GTFS table definitions
------------------------------------------------------------------------

------------------------------------------------------------------------
-- gtfs_agency: high-level information about the agency or agencies in
-- this GTFS schedule.
------------------------------------------------------------------------

CREATE TABLE gtfs_agency (
       id serial PRIMARY KEY,
       agency_id text UNIQUE,
       agency_name text NOT NULL,
       agency_url text NOT NULL,
       agency_timezone text NOT NULL,
       agency_lang text,
       agency_phone text,
       agency_fare_url text
);

------------------------------------------------------------------------
-- gtfs_stops: information about bus stops, most importantly location.
------------------------------------------------------------------------

CREATE TABLE gtfs_stops (
       stop_id text PRIMARY KEY,
       stop_code text,
       stop_name text NOT NULL,
       stop_desc text,
       stop_lat double precision NOT NULL,
       stop_lon double precision NOT NULL,
       zone_id text,
       stop_url text,
       location_type smallint,
       parent_station text references gtfs_stops(stop_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       stop_timezone text,
       wheelchair_boarding smallint
);

------------------------------------------------------------------------
-- gtfs_routes: high-level information about routes.
------------------------------------------------------------------------

CREATE TABLE gtfs_routes (
       route_id text PRIMARY KEY,
       agency_id text references gtfs_agency(agency_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       route_short_name text,
       route_long_name text,
       route_desc text,
       route_type smallint NOT NULL,
       route_url text,
       route_color varchar(6),
       route_text_color varchar(6),
       CHECK(route_short_name IS NOT NULL OR route_long_name IS NOT NULL)
);

------------------------------------------------------------------------
-- gtfs_shapes: lists of points corresponding to detailed route paths,
-- ideally smoothly interpolated along the path.
------------------------------------------------------------------------

CREATE TABLE gtfs_shapes (
       id serial PRIMARY KEY,
       shape_id text NOT NULL,
       shape_pt_lat double precision NOT NULL,
       shape_pt_lon double precision NOT NULL,
       shape_pt_sequence integer NOT NULL,
       shape_dist_traveled double precision,
       UNIQUE (shape_id, shape_pt_sequence)
);


------------------------------------------------------------------------
-- gtfs_trips: high-level information about specific trips.
------------------------------------------------------------------------

-- ensure we don't put non-existent shape IDs into the trips table
CREATE OR REPLACE FUNCTION shape_id_exists(sid text) RETURNS boolean
AS $$
DECLARE 
    n integer;
BEGIN
    IF (sid is NULL) THEN
        RETURN true;
    ELSE
        SELECT INTO n count(*) FROM gtfs_shapes WHERE shape_id = sid;
        RETURN (n>1);
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE gtfs_trips (
       route_id text NOT NULL references gtfs_routes(route_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       service_id text NOT NULL,
       trip_id text PRIMARY KEY,
       trip_headsign text,
       trip_short_name text,
       direction_id smallint,
       block_id text,
       shape_id text,
       wheelchair_accessible smallint,
       bikes_allowed smallint,
       CONSTRAINT good_shape_id CHECK (shape_id_exists(shape_id))
);

------------------------------------------------------------------------
-- gtfs_stop_times: arrival and departure times for all stops on all
-- trips
------------------------------------------------------------------------

CREATE TABLE gtfs_stop_times (
       id serial PRIMARY KEY,
       trip_id text NOT NULL references gtfs_trips(trip_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       arrival_time text,
       departure_time text,
       stop_id text NOT NULL references gtfs_stops(stop_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       stop_sequence integer NOT NULL,
       stop_headsign text,
       pickup_type smallint,
       drop_off_type smallint,
       shape_dist_traveled real,
       UNIQUE (trip_id, stop_sequence)
);

------------------------------------------------------------------------
-- gtfs_calendar: days and dates for specific service patterns.
------------------------------------------------------------------------


CREATE TABLE gtfs_calendar (
       service_id text PRIMARY KEY,
       monday text NOT NULL,
       tuesday text NOT NULL,
       wednesday text NOT NULL,
       thursday text NOT NULL,
       friday text NOT NULL,
       saturday text NOT NULL,
       sunday text NOT NULL,
       start_date text NOT NULL,
       end_date text NOT NULL
);

------------------------------------------------------------------------
-- gtfs_calendar_dates: dates for exceptions from normal schedule.
------------------------------------------------------------------------


CREATE TABLE gtfs_calendar_dates (
       id serial PRIMARY KEY,
       service_id text NOT NULL,
       date text NOT NULL,
       exception_type smallint NOT NULL,
       UNIQUE (service_id, date)
);

------------------------------------------------------------------------
-- gtfs_fare_attributes: metadata about fares.
------------------------------------------------------------------------


CREATE TABLE gtfs_fare_attributes (
       fare_id text PRIMARY KEY,
       price real NOT NULL,
       currency_type TEXT NOT NULL,
       payment_method smallint NOT NULL,
       transfers smallint,
       transfer_duration integer
);

------------------------------------------------------------------------
-- gtfs_fare_rules: which fares apply to which routes, stops, etc.
------------------------------------------------------------------------

CREATE TABLE gtfs_fare_rules (
       id serial PRIMARY KEY,
       fare_id text NOT NULL references gtfs_fare_attributes(fare_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       route_id text references gtfs_routes(route_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       origin_id text,
       destination_id text,
       contains_id text,
       UNIQUE (route_id, origin_id, destination_id, contains_id)
);

------------------------------------------------------------------------
-- gtfs_frequencies: information about headway-based trips.
------------------------------------------------------------------------

CREATE TABLE gtfs_frequencies (
       id serial PRIMARY KEY,
       trip_id text NOT NULL references gtfs_trips(trip_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       start_time text NOT NULL,
       end_time text NOT NULL,
       headway_secs double precision NOT NULL,
       exact_times smallint,
       UNIQUE (trip_id, start_time)
);

------------------------------------------------------------------------
-- gtfs_transfers: explicit transfer points.
------------------------------------------------------------------------

CREATE TABLE gtfs_transfers (
       id serial PRIMARY KEY,
       from_stop_id text NOT NULL references gtfs_stops(stop_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       to_stop_id text NOT NULL references gtfs_stops(stop_id)
       ON DELETE CASCADE ON UPDATE CASCADE,
       transfer_type smallint DEFAULT 0,
       min_transfer_time integer,
       UNIQUE (from_stop_id, to_stop_id)
);

------------------------------------------------------------------------
-- gtfs_feed_info: explicit transfer points.
------------------------------------------------------------------------

CREATE TABLE gtfs_feed_info (
       id serial PRIMARY KEY,
       feed_publisher_name text NOT NULL UNIQUE,
       feed_publisher_url text NOT NULL,
       feed_lang text NOT NULL,
       feed_start_date text,
       feed_end_date text,
       feed_version text
);

------------------------------------------------------------------------
-- gtfs_demand: demand, broken down by stop, hour, weekday, and route
-- groups.
------------------------------------------------------------------------

CREATE TABLE gtfs_demand (
       id serial PRIMARY KEY,
       route_id_list text NOT NULL,
       stop_id text NOT NULL,
       hour_bin int NOT NULL,
       day_type text NOT NULL,
       beta real NOT NULL
);

------------------------------------------------------------------------
-- gtfs_version: information about GTFS versioning. This data should be
-- maintained by the database insert script.
------------------------------------------------------------------------

CREATE TABLE gtfs_version (
       id serial PRIMARY KEY,
       version_id text NOT NULL UNIQUE,
       t_start timestamptz NOT NULL UNIQUE,
       description text,
       zip_path text,
       zip_bytes bigint
);

------------------------------------------------------------------------
