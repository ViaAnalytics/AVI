------------------------------------------------------------------------
--	Creates ALL the required tables and relations for setting up an
--      agency
------------------------------------------------------------------------

-- Automation : Nathan Skrzypczak

\set VERBOSITY terse

\ir avi_basic.sql
\ir gtfs.sql
\ir agency.sql
\ir history.sql
select create_all_history();

\ir gtfs_indices.sql
