import psycopg2
from psycopg2 import IntegrityError, InternalError, InterfaceError
from collections import deque
import time
import logging
from datetime import datetime
import dateutil.parser
import string


def convert_to_dts(msg):
    # recursively step throgh JSON message and convert time strings into
    # Python datetime objects
    time_vars = ['ts', 'sent_time', 'last_gps_time', 'detect_time',
                 'expire_time']
    if isinstance(msg, list):
        for i in range(len(msg)):
            msg[i] = convert_to_dts(msg[i])
    elif isinstance(msg, dict):
        for key in msg:
            if isinstance(msg[key], dict):
                msg[key] = convert_to_dts(msg[key])
            elif key in time_vars and type(msg[key]) is not datetime:
                msg[key] = dateutil.parser.parse(msg[key])

    return msg


def get_vals_list(msg, colnames):
    msg_dict = convert_to_dts(msg.msg)
    vals_list = []
    
    for key in colnames:
        for msg_key in msg_dict:
            if string.lower(key) == string.lower(msg_key):
                vals_list.append(msg_dict[msg_key])
                break
        else:
            # Never found key
            vals_list.append(None)

    return vals_list


class Message:
    def __init__(self, msg):
        self.msg = msg
        self.n_tries = 0
        self.t_start = time.time()

    def __str__(self):
        return "Message(%s)" % self.msg


# Class to store a queue of objects. Not threadsafe.
class MessageList:
    def __init__(self, t_wait):
        # time to wait before sending messages (seconds)
        self.t_wait = t_wait
        self._msg_list = deque()

    def add_to_bottom(self, msg):
        self._msg_list.appendleft(msg)

    def pop(self):
        if self.length() > 0:
            return self._msg_list.pop()

    def length(self):
        return len(self._msg_list)

    def ready(self):
        # make sure we have a message, and wait at least waitTime
        # seconds to send it
        ready = True
        length = self.length()
        if (length == 0):
            ready = False
        else:
            msg = self._msg_list[length-1]
            if (time.time() - msg.t_start < self.t_wait):
                ready = False
        return ready


class PostgresQueue:
    def __init__(self, dbname, host, user, password, logger_name, t_batch=1.5,
                 t_wait=0, max_tries=3):
        self.t_batch, self.t_wait = t_batch, t_wait
        self.t_prev, self.max_tries = 0, max_tries

        # Reconnect to Postgres once every hour, in order not to leave
        # connections open for a very long time
        self.t_prev_pg = time.time()
        self.t_reconnect = 60*60.

        self.logger = logging.getLogger(logger_name)

        self.logger.info("Initializing PostgresQueue...")
        self.logger.info("Connecting to Postgres...")

        vals = (dbname, host, user, password)
        self.conn_str = "dbname='%s' host='%s' user='%s' password='%s'" % vals
        self.conn = psycopg2.connect(self.conn_str)
        
        # Dicts of message lists and column names.
        self.msg_lists = {}
        self.colnames = {}
        # These correspond to names of tables in postgres.
        self.tables = ['raw_location', 'projected_location', 'exist', 'event']

        for tbl in self.tables:
            self.add_table(tbl)

            
    def add_table(self, table):
        self.colnames[table] = self.get_col_names(table)
        self.msg_lists[table] = MessageList(self.t_wait)

        
    def get_col_names(self, table):
        query = "SELECT * FROM " + table + " LIMIT 0"
        
        cur = self.conn.cursor()
        cur.execute(query)
        
        colnames = [desc[0] for desc in cur.description]

        # columns handled only on postgres side
        pg_cols = ['id', 'insert_ts']
        for col in pg_cols:
            if col in colnames:
                colnames.remove(col)
        
        cur.close()
        return colnames

    
    def enqueue(self, message, table):
        self.logger.debug("Pushing message %s to queue..." % (message))

        # Cast into Message object:
        if type(message) is dict:
            message = Message(message)

        if table is not None:
            self.msg_lists[table].add_to_bottom(message)
        else:
            self.logger.warn("table is None!")

    
    def insert_batch(self, cur, vals_lists, table):
        # format ordered values into INSERT string:
        s_li = ["%s" for i in range(len(self.colnames[table]))]
        s_str = '(' + ','.join(s_li) + ')'
        cols = '(' + ','.join(self.colnames[table]) + ')'

        vals_str = ','.join(cur.mogrify(s_str, v_li) for v_li in vals_lists)
        exec_str = "INSERT INTO " + table + " " + cols + " VALUES " + vals_str
        self.logger.info("Inserting batch of %d %s messages" % (len(vals_lists),
                                                                table))

        cur.execute(exec_str)

    
    def get_send_msgs(self, table):
        li = self.msg_lists[table]
        msgs = []
        while li.ready():
            message = li.pop()
            msgs.append(message)
        return msgs

    
    def get_batch_vals_list(self, table, msgs):
        all_li = []
        for msg in msgs:
            all_li.append(get_vals_list(msg, self.colnames[table]))
            self.logger.debug("Add message to %s batch" % (table))
            self.logger.debug("%s" % (msg))
        return all_li


    def requeue_msgs(self, msgs, table):
        for msg in msgs:
            # re-add each message to queue, unless we've attempted to 
            # insert it too many times already
            if msg.n_tries == self.max_tries:
                continue
            msg.n_tries += 1
            self.enqueue(msg, table)

    
    def clear_queue(self, table):
        msgs = self.get_send_msgs(table)
        if len(msgs) > 0:
            vals_li = self.get_batch_vals_list(table, msgs)
            try:
                cur = self.conn.cursor()
                self.insert_batch(cur, vals_li, table)
                self.conn.commit()
                cur.close()
            except (IntegrityError, InternalError):
                self.logger.exception("Failed to insert badly-formatted data! Rolling back.")
                self.conn.rollback()
                cur.close()
                self.requeue_msgs(msgs, table)
            except InterfaceError:
                self.logger.exception("Lost connection somehow! Reconnecting.")
                self.requeue_msgs(msgs, table)
                self.reconnect()
            except Exception:
                self.logger.exception("Error trying to insert! Rolling back, reconnecting.")
                self.requeue_msgs(msgs, table)
                self.conn.rollback()
                cur.close()
                self.reconnect()


    def reconnect(self):
        try:
            self.conn = None
            self.conn = psycopg2.connect(self.conn_str)
            self.t_prev_pg = time.time()
        except Exception:
            self.logger.exception("Couldn't reconnect!")

    
    def loop(self):
        t = time.time()
        try:
            # reconnect every once in a while
            if t - self.t_prev_pg > self.t_reconnect:
                self.logger.warning("Performing timer-based reconnect")
                self.reconnect()

            # clear message queues
            if t - self.t_prev > self.t_batch:
                for table in self.msg_lists:
                    self.clear_queue(table)
                self.t_prev = t
        except:
            self.logger.exception("Unexpected error! Trying to reconnect.")
            self.reconnect()
