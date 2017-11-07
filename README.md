# AVI: The Open-Source AVL System

AVI is an open-source project designed to serve as a complete AVL system for your transit agency, when combined with Android devices installed on transit vehicles.

AVI has two primary external input requirements:
  * A planned schedule in [GTFS](https://developers.google.com/transit/gtfs/) format.
  * Daily trip assignments for each vehicle/device.

The primary data output of AVI is a [GTFS realtime (GTFS-rt)](https://developers.google.com/transit/gtfs-realtime) feed of vehicle locations and estimated arrival predictions. This feed is suitable for use in [OneBusAway](http://onebusaway.org/) or other passenger information applications.

Internally, AVI consists of several main components:

1. AVI Vehicle, an Android app which sends home real-time locations from each vehicle.
2. AVI Server, a Java application which uses the real-time flow of locations to track vehicles as they follow their scheduled routes.
3. Python scripts which manage and manipulate various data flows.
4. An MQTT broker for real-time messaging.
5. A Postgres database.

AVI is a complex system with a large number of moving parts, and familiarity with Java, Android, Python, SQL, and database and server administration will be useful if not critical in deploying and maintaining it. AVI was originally developed by [VIA Analytics](www.v-a.io).

## Deployment procedure

The following describes a minimal set of steps required to set up AVI. The overall order is not critical, though certain components depend on others (e.g., many other components depend on the MQTT broker). All components are discussed in more detail below.

1. Deploy an MQTT broker.
2. Deploy a Postgres database, and prepare tables using the provided AVI schema.
  * Upload the GTFS schedule to Postgres using the AVI GTFS upload utility.
3. Configure and compile the AVI Vehicle Safety Net apk, and copy this apk into the assets folder of the main AVI Vehicle app.
4. Configure and compile the AVI Vehicle Android app.
5. Install AVI Vehicle on Android devices and mount them in the vehicle with power and internet connections. (We recommend that you procure rooted Android devices, or root them yourself.)
  * Set the associations between transit vehicle IDs and Android device IDs in the Postgres database.
6. Deploy two server-side Python scripts:
  * pb_to_json to convert raw protobuf vehicle messages into human-readable JSON.
  * json_to_postgres to insert appropriate JSON messages into the Postgres database.
7. Write and deploy script to insert daily trip assignments into the Postgres database. (An example Python script is provided, but it will need to be heavily customized for whatever data source you are using. This example script is merely provided for convenience -- if you are more comfortable with scripting in another language, feel free to write your own script.)
8. Configure, compile, and deploy the AVI Server Java application.
9. Deploy the GTFS-rt feed generator and make the GTFS-rt feed publically available.

For a small transit agency, all of the server-side utilities could run on a single reasonably large server. For larger agencies, separate servers for the database, MQTT broker, the Python scripts, AVI Server, and the GTFS-rt feed generator may be appropriate.

## General considerations

During the preparation stage, you should choose a “shortname” for your agency. This should be a simple token or acronym with no spaces or punctuation. For example, the TriMet transit agency in Portland, OR might choose `trimet`, or Boston’s transit agency might choose mbta. This shortname is used in many places in the various applications (and this documentation) and needs to be consistent.

## MQTT broker

Real-time data in AVI generally flows through [MQTT](http://mqtt.org/), which is a lightweight real-time pub/sub messaging mechanism with a single central “message broker” and a potentially large number of connected clients. The clients can “publish” messages to specific topics and/or “subscribe” to specific topics to receive any published messages in real-time.

We have successfully used the [Mosquitto MQTT broker](https://mosquitto.org/) in the past, which is part of the Eclipse open-source project.

As written, the code does not use SSL security for MQTT connections. If you do want to use SSL, modifications would have to be made to AVI Vehicle, AVI Server, and the Python data utility scripts `pb_to_json` and `json_to_postgres`. You would also need to generate SSL keys and distribute those with the various scripts.

If you do not use SSL security, we recommend that you use the configuration mechanisms of your chosen MQTT broker to create one or more “superusers” with a strong password which have publish/subscribe access to all topics, while strictly limiting “anonymous” clients to publishing on the specific topics `<agency_shortname>/pb/<client_id>/rl` (for location messages) and `<agency_shortname>/pb/<client_id>/exist` (for status messages), with no ability to subscribe to any topics. The superuser(s) should be used for the various server-side scripts, while the Android devices running AVI Vehicle would default to anonymous users, using their Android device ID as an MQTT client ID. In Mosquitto, this would be done through a combination of the password and ACL configuration files.

## Postgres database

Various kinds of data are in the central AVI database, including historical locations, arrival and departure events, daily trip assignments, and schedule information. We use the open-source [Postgres SQL database](https://www.postgresql.org/). Instructions for installing and deploying a Postgres database can be found online. We have used [Amazon RDS](https://aws.amazon.com/rds/postgresql/) successfully in the past. After the database server is initially prepared, create a new Postgres database called <agency_shortname>. The table schemas are stored in the `pg_schema` folder. To prepare the empty tables, log into the agency AVI database with `psql` and run `\ir /path/to/create_all.sql`.

Make sure that you choose an appropriate size for your database. The largest contributor to the size of the database will be the archived raw locations. Each location will require roughly 130 bytes to store in Postgres, and each Android device will generate approximately 5800 raw locations per day (using the default 15-second location cadence). This comes to 750 kB per day, and thus an agency with 100 vehicles would generate 75 MB of raw location data per day. Including other types of data, this quantity could increase by a factor of 2. Thus, to maintain a reasonable database size, old data will need to be backed up and removed from the primary database on a regular basis.

## Working with bundled Python utilities

**Note**: the following deployment suggestions assumes that you are using a Linux or Unix server. We have not used Windows servers, and some of this advice will likely have to be modified in that environment.

Each of the included Python utilities has a set of dependencies. We highly recommend managing these dependencies using [Python virtual environments](https://virtualenv.pypa.io/en/stable/) and the [pip package manager](https://pypi.python.org/pypi/pip). Use of virtual environments allows the administrator to maintain a separate set of dependencies for each script.

We recommend installing `pip` globally using your distribution’s package manager, and then installing `virtualenv` globally using `pip`.

The general procedure for creating and preparing a virtual environment for a given utility is:

1. Create a virtual environment for a particular utility using the virtualenv Python package in a chosen location on your server’s filesystem.
2. Activate the just-created virtual environment by running `source /path/to/virtualenv/bin/activate`.
3. Each utility comes with a `requirements.txt` file specifying its dependencies. Install the dependencies by running `pip install -r /path/to/requirements.txt`.

To run a given script, you must either first activate the relevant virtual environment as in step #2 above and then run the script normally, or explicitly provide the full path to the python binary within the virtual environment (specifically located at `/path/to/virtualenv/bin/python`). The first approach only works if you are running the script interactively.

### Deployment of real-time utilities

A few utilities need to be running permanently for message routing to work properly. For server administration and monitoring, we have used a combination of [Upstart](http://upstart.ubuntu.com/) and [Monit](https://mmonit.com/monit/). Upstart is an Ubuntu-specific utility which simplifies the procedure of starting or starting certain scripts automatically. Other Linux distributions provide similar functionality via different utilities.

Monit is a more full-featured monitoring utility, which can be configured to automatically restart scripts which have crashed, and send email or other notifications to an administrator when problems are detected. Monit would need to be installed, e.g. via your distribution’s package manager. Monit requires a process ID file, or pidfile, to know which Linux process it should be monitoring for a given script. Each of the Python utilities can be configured to write such a pidfile. The Java-based AVI Server code should be managed similarly.

Sample Upstart and Monit configuration files for a hypothetical script have been provided to demonstrate the monitoring logic.

### Working with GTFS
After the Postgres database is prepared, you will need to insert the GTFS data into the Postgres database using the provided `gtfs_uploader` Python utility code. This performs several important functions:

1. ensures that the various GTFS tables in the database reflect the current actual schedule
2. attempts to perform a “minimal required update,” so that shapes, trips, etc., are not modified if they exist in both the previous GTFS schedule and the new schedule
3. cleans shapes by removing unnecessary shape points
4. calculates the `shape_dist_traveled` field for shapes and stop times (in meters), ignoring any pre-existing value in the GTFS schedule
5. keeps track of historical GTFS schedules in the `GTFS_<table>_history` tables, along with the time ranges during which they are/were active in the database. (This functionality is actually handled at the Postgres level rather than in the `gtfs_uploader` script.)

Once you have inserted the current GTFS schedule in the Postgres database, you will need to build a local SQLite GTFS bundle, to be used by the AVI Server application. This can be done using the `bundle_builder` Python utility.

## Trip assignments

AVI Server relies on trip assignments in the Postgres database, which associate vehicles with the service they are providing on a given day. In general, these assignments should be populated at least daily but generally more frequently (for example, if a vehicle is re-assigned to cover different service in the middle of the daily). You are free to populate the `trip_assignment` table in whatever manner you like.

We have provided a sample Python script which is not runnable as-is. This script assumes that there is some kind of API from which the current trip assignments can be determined. This could be a HTTP REST API, files on an FTP server, or queries to a CAD database. You would have to write the code to access this API and convert it into the format assumed by the `trip_assigner` example script. The sample script gets data from this API every few minutes and updates the trip assignments in the Postgres database when necessary.

Use of the sample script is purely optional, and is provided only as a guide to the kind of data management that you will need to perform to manage daily trip assignments. Similarly, you may use any programming language you like to write this script if you are not comfortable with Python.

## AVI Vehicle Safety Net

The Safety Net is an Android application which is designed to run in tandem with the main AVI Vehicle application. It ensures that the vehicle-side components of the system are constantly up-and-running. If the AVI Vehicle app crashes, the Safety Net will automatically restart the main AVI Vehicle app. Furthermore, if the Android device is rooted, then the Safety Net will automatically be installed and started.

Important: if you change the application package of AVI Vehicle as recommended (see AVI Vehicle section below), then you will also need to modify the `AviPackage` variable in `src/com/via/avi/safetynet/Util.java`, compile a new `SafetyNet.apk`, and copy it into the AVI Vehicle assets directory.

## AVI Vehicle

Before compilation, two configuration files should be customized for your agency, both located in the assets directory: `avi.config` and `avi.mqtt_config`. Many configuration options can be provided, but only a few are required, such as the agency shortname, time zone, and the hostname of the MQTT broker.

The default application package is `com.via.avi`. We recommend changing the name of the AVI Vehicle application package to `com.via.avi.<agency_shortname>`, because application packages should be logically distinct across different agency installations. When you make this change, you should change the corresponding package name in the AVI Vehicle Safety Net (see above) and compile a new `SafetyNet.apk` file to be included in the assets folder.

### Compilation and code dependencies

We have used Eclipse to compile the code into an executable apk file. AVI Vehicle depends on the Google Play Services Library and the included MqttService library. Both of these will need to be imported into Eclipse as “library projects” and added to the AVI Vehicle application build path before compilation will succeed. Similarly, the included JARs in the `lib` directory must be added to the build path. The codebase has not been compiled using in the newer Android Studio environment. Because Google has encouraged Android developers to migrate from Eclipse to Android Studio, this could be a useful place for an interested developer to contribute.

**Note**: Both AVI Vehicle and AVI Server (see below) depend on the MqttService internal utility library. If you are using Eclipse, we recommend loading the MqttService code into Eclipse as a “library project,” then adding it to the build path of both AVI Vehicle and AVI Server.

### Android device considerations

The AVI Vehicle code has been tested on devices from Android 4.0 through 6.0.

We strongly recommend that these devices be rooted. Having root privileges allows the AVI Vehicle app more control over various device functionalities and thus provides more stability (for example, auto-booting upon receiving power). It also allows for mechanisms to update the AVI Vehicle app remotely with no physical interaction.

We have used consumer-grade Android devices with previous installations successfully. They may be cheaper and easier to procure in small quantities. However, many consumer devices are quite inconvenient to root. They may also be more difficult to procure in bulk, or to equip with cellular data connections. Finally, consumer-grade hardware may be less sturdy than enterprise-grade hardware, though we have used consumer-grade hardware for several years in existing projects with fairly minimal repair/replacement needs.

It will likely be useful to have a mechanism to update AVI Vehicle remotely, assuming you use rooted devices. By default, AVI Vehicle comes integrated with [Push-Link](https://www.pushlink.com/), which is a commercial web service (with which we have no affiliation) providing remote update functionality for Android devices. You may prefer to use another remote update mechanism or build your own, but this will require modifications to the Android code.

Every Android device has a unique serial number. We use this serial number as the “device ID” in various places, most importantly the messages sent by the AVI Vehicle devices. For a given device, this can be determined either by navigating to “Settings” -> “Device Info” -> “Status” on the device, or by connecting the device to a computer and running the `adb devices` command. When installing AVI Vehicle, make a note of the device ID of each device and the corresponding vehicle ID of the vehicle that it is installed in. These associations must be stored in the `devices` table of the Postgres database.

## AVI Server

AVI Server is the central brains of the code, which takes the raw data from vehicles, compares it to the GTFS schedules, and processes it into useful information, especially information about current and historical delays. It is a Java application which runs in real-time. As inputs, it requires a stream of raw locations, daily trip assignments, and a SQLite GTFS “bundle.” It will then produce a real-time stream of arrival and departure events as well as projected locations as outputs. “Projected” locations are so-named because they have been forced to lie on the GTFS shape, at the best-guess location given the current raw location and previous location history when available.

All the libraries required by AVI Server come bundled as JAR files, with the exception of MqttService, an internal utility library (see note under AVI Vehicle above). We recommend loading the AVI Server code into Eclipse and adding MqttService and the included JARs to the AVI Server build path. Then, you can use Eclipse to compile AVI Server and its dependencies into a single executable JAR file.

AVI Server takes two configuration files as inputs. The first is low-level, and contains information required to connect to the Postgres database, the MQTT broker, and the local SQLite GTFS bundle. The second configuration file is high-level, and contains parameters controlling the detailed projection and event generation, such as the agency’s time zone, the minimum number and frequency of locations received, typical vehicle speeds, the size of buffer zones around stops, how much delay is acceptable, etc. Example configuration files are provided but they will need to be modified to work for your agency.

To run AVI Server in production, we recommend a combination of Upstart+Monit, similar to the Python utility scripts,. Note that if you wish to use Monit for monitoring, you will have to wrap the execution of the JAR in a shell script (e.g. bash) which also writes its own process ID to a file. We recommend an Upstart script along the lines of [this one](http://www.jcgonzalez.com/linux-java-service-wrapper-example).

## GTFS-rt feed generator

GTFS-rt is a format originally created by Google as a standard to incorporate real-time information from agencies, and particularly arrival predictions, in Google Maps. It now sees sufficiently wide use to constitute an effective standard. 

The GTFS-rt feed generator is a Python utility designed to consume the real-time stream of projected locations and events and produce a GTFS-rt feed. The resulting feed is written to two files: one in the canonical Google protobuf format, and a second in human-readable text form for debugging.

The feed generator does not do any projection to future times. It merely reflects the current state of the system, in the form of the most recent location and arrival or departure event for each vehicle. According to the GTFS-rt specification, if estimates for future arrivals are not provided in the feed (as in this feed generator), the delay is simply assumed to stay constant along the trip. This constitutes a simple but effective “prediction” mechanism in many circumstances, and it is difficult to get better estimates without a sophisticated prediction system. If your agency wishes to provide smarter arrival estimates to passengers, you would need to generate those predictions externally and then incorporate them into this GTFS-rt feed.

The feed generator utility does not provide a public API in itself. To make the feed publically available, one would need to make the GTFS-rt protobuf file accessible via an HTTP request. Depending on your agency’s technology stack, you may want to do this via API technologies you are already familiar with. Alternatively, you could make the protobuf file available using a web server like Apache or nginx with a very simple configuration.
