from argparse import ArgumentParser
import logging
import logging.handlers
import time
import sys
import os
from pb_converter import ProtobufConverter

if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("--host", dest="host", 
                        metavar="HOST",
                        required=True,
                        help="hostname of Mosquitto broker")
    parser.add_argument("--port", dest="port", 
                        metavar="PORT",
                        default=1883,
                        type=int,
                        help="port of Mosquitto broker")
    parser.add_argument("--agency", dest="agency", 
                        metavar="AGENCY",
                        required=True,
                        help="shortname of agency")
    parser.add_argument("--user", dest="username", 
                        required=True,
                        metavar="USER",
                        help="username for Mosquitto broker")
    parser.add_argument("--password", dest="password", 
                        required=True,
                        metavar="PASSWORD",
                        help="password for Mosquitto broker")
    parser.add_argument("--log-file", dest="logfile", 
                        metavar="LOGFILE",
                        default=None,
                        help="optional file for log messages")
    parser.add_argument("--log-level", dest="loglevel", 
                        metavar="LOGLEVEL",
                        default="WARNING",
                        help="log level of Predictor (one of CRITICAL, ERROR, WARNING, INFO, or DEBUG)",
                        choices=('CRITICAL', 'ERROR', 'WARNING', 'INFO', 'DEBUG'))
    parser.add_argument("--pid-dir", dest="piddir", 
                        metavar="PIDDIR",
                        default=None,
                        help="optional directory for pidfile, e.g. '/home/ubuntu/run/'")
    
    args = parser.parse_args()

    pid = str(os.getpid())
    pidfilename = "%s_pb_to_json.pid" % (args.agency)
    piddir = args.piddir

    if piddir is not None:
        pidfile = piddir + pidfilename
    
        file(pidfile, 'w').write(pid)
    
    logger_name = '%s_pb_to_json' % (args.agency)
    
    logger = logging.getLogger(logger_name)

    logLevel = getattr(logging, args.loglevel)
    logfile = args.logfile

    if (logfile is not None):
        formatter = logging.Formatter('%(asctime)s [%(process)d]: %(message)s')
        log_handler = logging.handlers.WatchedFileHandler(logfile)
        log_handler.setFormatter(formatter)
        log_handler.setLevel(logLevel)
        logger.addHandler(log_handler)
        logger.setLevel(logLevel)
    else:
        logging.basicConfig(format='%(asctime)s %(message)s',
                            level=logLevel)
    
    listener = ProtobufConverter(args.host, args.port, args.username,
                                 args.password, args.agency, logger_name)

    listener.start()
    
    while True:
        time.sleep(0.01)
