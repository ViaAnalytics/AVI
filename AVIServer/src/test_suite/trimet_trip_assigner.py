import logging
import time
import pytz
from rawloc_trip_assigner import RawlocTripAssigner

if __name__=='__main__':
    old_agency = 'trimet'
    agency = 'trimet_test'
    mqtt_host = '54.244.255.7'
    mqtt_user = 'via'
    mqtt_pass = 'prestotesto12'

    pg_host = 'data-pipe-1.cueepsqael4s.us-west-2.rds.amazonaws.com'
    pg_user = 'via'
    pg_pass = 'prestotesto12'

    tz = pytz.timezone('America/Los_Angeles')
    zero_hour = 3
    
    log_level = 'DEBUG'

    logger_name = '%s_old_json_to_new_pb' % (old_agency)
    logger = logging.getLogger(logger_name)
    logging.basicConfig(format='%(asctime)s %(message)s',
                        level=getattr(logging, log_level))

    rlta = RawlocTripAssigner(mqtt_host, mqtt_user, mqtt_pass, agency,
                              old_agency, pg_host, pg_user, pg_pass, tz,
                              zero_hour, logger_name)
    rlta.start()
    while True:
        time.sleep(0.01)
