import logging
import time
from old_json_to_new_pb import OldJsonToNewPb

if __name__=='__main__':
    old_agency = 'trimet'
    agency = 'trimet_test'
    mqtt_host = '54.244.255.7'
    mqtt_user = 'via'
    mqtt_pass = 'prestotesto12'
    log_level = 'INFO'

    logger_name = '%s_old_json_to_new_pb' % (old_agency)
    logger = logging.getLogger(logger_name)
    logging.basicConfig(format='%(asctime)s %(message)s',
                        level=getattr(logging, log_level))

    converter = OldJsonToNewPb(mqtt_host, mqtt_user, mqtt_pass, agency,
                               old_agency, logger_name)
    converter.start()
    while True:
        time.sleep(0.01)
