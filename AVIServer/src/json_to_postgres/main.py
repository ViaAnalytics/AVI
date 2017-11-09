from argparse import ArgumentParser
import logging
import logging.handlers
import time
import os
from json_listener import JsonListener
from pg_queue import PostgresQueue

if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("--mqtt-host", dest="mqtt_host", 
                        metavar="MQTT_HOST",
                        required=True,
                        help="hostname of Mosquitto broker")
    parser.add_argument("--mqtt-port", dest="mqtt_port", 
                        metavar="MQTT_PORT",
                        default=1883,
                        type=int,
                        help="port of Mosquitto broker")
    parser.add_argument("--mqtt-user", dest="mqtt_user", 
                        required=True,
                        metavar="MQTT_USER",
                        help="username for Mosquitto broker")
    parser.add_argument("--mqtt-pass", dest="mqtt_pass", 
                        required=True,
                        metavar="MQTT_PASS",
                        help="password for Mosquitto broker")
    parser.add_argument("--agency", dest="agency", 
                        metavar="AGENCY",
                        required=True,
                        help="shortname of agency")
    parser.add_argument("--log-file", dest="logfile", 
                        metavar="LOGFILE",
                        default=None,
                        help="file for log messages")
    parser.add_argument("--log-level", dest="loglevel", 
                        metavar="LOGLEVEL",
                        default="WARNING",
                        help="log level of Predictor (one of CRITICAL, ERROR, WARNING, INFO, or DEBUG)",
                        choices=('CRITICAL', 'ERROR', 'WARNING', 'INFO', 'DEBUG'))
    parser.add_argument("--pid-dir", dest="piddir", 
                        metavar="PIDDIR",
                        default=None,
                        help="directory for PID, like '/home/ubuntu/run/'")
    parser.add_argument("--pg-host", dest="pg_host",
                        required=True,
                        metavar="PG_HOST",
                        help="URI for postgres")
    parser.add_argument("--pg-user", dest="pg_user",
                        required=True,
                        metavar="PG_USER",
                        help="username for postgres")
    parser.add_argument("--pg-pass", dest="pg_pass",
                        required=True,
                        metavar="PG_PASS",
                        help="password for postgres")

    args = parser.parse_args()

    pid = str(os.getpid())
    pidfilename = "%s_all_json_to_postgres.pid" % (args.agency)
    piddir = args.piddir

    if piddir is not None:
        pidfile = piddir + pidfilename
    
        file(pidfile, 'w').write(pid)    
    
    logger_name = '%s_all_json_to_postgres' % (args.agency)
    
    logger = logging.getLogger(logger_name)

    logLevel = getattr(logging, args.loglevel)
    logfile = args.logfile

    if logfile is not None:
        formatter = logging.Formatter('%(asctime)s [%(process)d]: %(message)s')
        log_handler = logging.handlers.WatchedFileHandler(logfile)
        log_handler.setFormatter(formatter)
        log_handler.setLevel(logLevel)
        logger.addHandler(log_handler)
        logger.setLevel(logLevel)
    else:
        logging.basicConfig(format='%(asctime)s %(message)s',
                            level=logLevel)
            
    listener = JsonListener(args.mqtt_host, args.mqtt_port, args.mqtt_user,
                            args.mqtt_pass, args.agency, logger_name)

    dbname = args.agency
    pq = PostgresQueue(dbname, args.pg_host, args.pg_user, 
                       args.pg_pass, logger_name, 1.5, 0)
    listener.set_queue(pq)
    
    listener.start()
    
    while True:
        time.sleep(0.01)
        pq.loop()
