------------------------------------------------------------------------
--	Definition of agency management tables for AVI Server
------------------------------------------------------------------------


---------------------------------------------------------------
-- device: canonical list of devices
---------------------------------------------------------------

CREATE TABLE device (
      id text PRIMARY KEY,
      make text,
      model text,
      description text
);

---------------------------------------------------------------
-- vehicle: canonical list of vehicles, including the device assigned to
-- each vehicle, where appropriate.
---------------------------------------------------------------

CREATE TABLE vehicle (
       id text PRIMARY KEY,
       make text,
       model text,
       manufactured_year int,
       description text,
       license_number text,
       device_id text REFERENCES device(id)
);

---------------------------------------------------------------
-- driver: canonical list of drivers
---------------------------------------------------------------

CREATE TABLE driver (
       id text PRIMARY KEY,
       first_name text,
       last_name text
);

---------------------------------------------------------------
-- trip_assignment: associations between specific vehicles and trips
-- on a given date, ideally including the driver information.
---------------------------------------------------------------

CREATE TABLE trip_assignment (
       id serial PRIMARY KEY,
       trip_id text NOT NULL, 
       active_date date NOT NULL,
       vehicle_id text NOT NULL references vehicle(id),
       driver_id text references driver(id),
       UNIQUE (trip_id, active_date)
);
