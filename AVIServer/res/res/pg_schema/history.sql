------------------------------------------------------------------------
-- SQL procedures to generate history tables and history triggers for keeping track of modifications
------------------------------------------------------------------------

-- schema : Jacob Lynn
-- automation : Nathan Skrzypczak

-- To create everything (for gtfs_* and device, driver, vehicle, trip_assignment)
-- SAMPLE USAGE : select create_all_history --

-- Create the history table fitting to a table with the name given as parameter
-- SAMPLE USAGE : select create_history_table('gtfs_agency');

-- Create the history triggers
-- SAMPLE USAGE : select create_history_triggers('gtfs_agency');


CREATE OR REPLACE FUNCTION create_all_history () RETURNS void AS $$
    DECLARE
	tabls information_schema.tables;
	nothing record;
	tblname text;
    BEGIN
	raise warning 'CREATING HISTORY';
	FOR tabls IN SELECT information_schema.tables.* from information_schema.tables 
	where table_schema = 'public' LOOP
	    IF (substring(tabls.table_name from 1 for 5) = 'gtfs_' AND position('history' in tabls.table_name) = 0)
	    OR tabls.table_name = 'device'
	    OR tabls.table_name = 'driver'
	    OR tabls.table_name = 'vehicle'
	    OR tabls.table_name = 'trip_assignment' THEN
	      raise warning 'creating history for table %', tabls.table_name;
	       perform create_history_table(tabls.table_name);
	       perform create_history_triggers(tabls.table_name);
	    END IF;
	END LOOP;
    END;
$$ LANGUAGE plpgsql;

------------------------------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION create_history_table (tablename text) RETURNS void AS $$
    DECLARE
	-- field information_schema.columns%ROWTYPE;
	field record;
	create_statement text;
	pks pg_attribute.attname%TYPE;
	pktuple text;
    BEGIN
	create_statement := 'CREATE TABLE ' || tablename || '_history (history_id serial PRIMARY KEY, t_range tstzrange NOT NULL';
	FOR field IN SELECT ic.table_name, ic.column_name, ic.data_type, ic.is_nullable, ic.udt_name FROM information_schema.columns AS ic WHERE table_name = tablename LOOP
	   create_statement := create_statement || ', ' || field.column_name || ' ' || field.data_type;
	   IF field.is_nullable = 'NO' THEN
	       create_statement := create_statement || ' NOT NULL';
	   END IF;
	END LOOP;
	create_statement := create_statement || ');' || 'CREATE INDEX ' || tablename || '_history_t_range ON '|| tablename ||'_history USING gist (t_range);' || 'CREATE INDEX ' || tablename || '_history_' || tablename || '_id ON ' || tablename || '_history (';
	pktuple := '';
	FOR pks IN SELECT pg_attribute.attname
	    FROM pg_index, pg_class, pg_attribute 
	    WHERE 
	    	  pg_class.oid = tablename::regclass AND
	  	  indrelid = pg_class.oid AND
		  pg_attribute.attrelid = pg_class.oid AND 
		  pg_attribute.attnum = any(pg_index.indkey)
            AND indisprimary LOOP
	    IF pktuple = '' THEN
	       pktuple := pks;
	    ELSE
	       pktuple := pktuple || ',' || pks;
	    END IF;
	END LOOP;
	create_statement := create_statement || pktuple || ');';

	IF NOT EXISTS (SELECT * FROM information_schema.tables where table_schema = 'public' AND table_name = tablename || '_history') THEN
		EXECUTE create_statement;
		RAISE NOTICE '(Created) TABLE : %', tablename || '_history';
	ELSE
		RAISE NOTICE '(Skipped) TABLE % [already exists]' , tablename || '_history';  
	END IF;
   END;
$$ LANGUAGE plpgsql;

------------------------------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION create_history_triggers (tablename text) RETURNS void AS $$
    DECLARE
	field information_schema.columns%ROWTYPE;
	pks pg_attribute.attname%TYPE;
	create_statement text;
	fieldlist text;
	newdotfieldlist text;
	pk_conditions text;
    BEGIN
	fieldlist := '';
	newdotfieldlist := '';
	FOR field IN SELECT * from information_schema.columns where table_name = tablename LOOP
	    newdotfieldlist := newdotfieldlist || ', NEW.' || field.column_name;
	    fieldlist := fieldlist || ',' || field.column_name;
	END LOOP;
	pk_conditions := '';
	FOR pks IN SELECT pg_attribute.attname
	    FROM pg_index, pg_class, pg_attribute 
	    WHERE 
	    	  pg_class.oid = tablename::regclass AND
	  	  indrelid = pg_class.oid AND
		  pg_attribute.attrelid = pg_class.oid AND 
		  pg_attribute.attnum = any(pg_index.indkey)
            AND indisprimary LOOP
	    IF pk_conditions = '' THEN
	       pk_conditions := pk_conditions || pks || ' = OLD.' || pks;
	    ELSE
	       pk_conditions := pk_conditions || ',' || pks || ' = OLD.' || pks;
	    END IF;
	END LOOP;
	create_statement := 'CREATE OR REPLACE FUNCTION process_'||tablename||'_history() RETURNS TRIGGER AS $'||tablename||'_history$ DECLARE prev_id integer; BEGIN IF (TG_OP = ''DELETE'') THEN SELECT INTO prev_id history_id FROM '||tablename||'_history WHERE '||pk_conditions||' AND now() <@ t_range LIMIT 1; UPDATE '||tablename||'_history SET t_range = tstzrange(lower(t_range), now()) WHERE history_id = prev_id; RETURN OLD; ELSIF (TG_OP = ''UPDATE'') THEN SELECT INTO prev_id history_id FROM '||tablename||'_history WHERE '||pk_conditions||' AND now() <@ t_range LIMIT 1; UPDATE '||tablename||'_history SET t_range = tstzrange(lower(t_range), now()) WHERE history_id = prev_id; INSERT INTO '||tablename||'_history (t_range '||fieldlist||') SELECT tstzrange(now(), ''infinity'') '||newdotfieldlist||'; RETURN NEW; ELSIF (TG_OP = ''INSERT'') THEN INSERT INTO '||tablename||'_history (t_range '||fieldlist||') SELECT tstzrange(now(), ''infinity'') '||newdotfieldlist||'; RETURN NEW; END IF; RETURN NULL; END; $'||tablename||'_history$ LANGUAGE plpgsql;';
	RAISE NOTICE '(Created) TRIGGER FUNCTION : %', 'process_'||tablename||'_history()';

	IF NOT EXISTS (select * from pg_trigger where tgname = tablename || '_history') THEN
		-- History trigger
		create_statement := create_statement || 'CREATE TRIGGER '||tablename||'_history AFTER INSERT OR UPDATE OR DELETE ON '||tablename||' FOR EACH ROW EXECUTE PROCEDURE process_'||tablename||'_history();';
		RAISE NOTICE '(Created) TRIGGER : %', tablename || '_history';
	ELSE
		RAISE NOTICE '(Skipped) TRIGGER % [already exists]' , tablename || '_history';  
	END IF;

	IF NOT EXISTS (select * from pg_trigger where tgname = tablename || '_no_redundant_updates') THEN
	   -- no redundancy in updates trigger
	   create_statement := create_statement || 'CREATE TRIGGER '||tablename||'_no_redundant_updates BEFORE UPDATE ON '||tablename||' FOR EACH ROW EXECUTE PROCEDURE suppress_redundant_updates_trigger();';
		RAISE NOTICE '(Created) TRIGGER : %', tablename || '_no_redundant_updates';
	ELSE
		RAISE NOTICE '(Skipped) TRIGGER % [already exists]' , tablename || '_no_redundant_updates';  
	END IF;

	EXECUTE create_statement;
    END;
$$ LANGUAGE plpgsql;
