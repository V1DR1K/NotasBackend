-- Run as a database administrator on the Whatplan database (whereFood).
-- This lets Notes' repository browser inspect all tenant rows while leaving
-- Whatplan's normal application role and its row-level security policies intact.
-- The role cannot log in and is not a superuser; it inherits the existing
-- migrator/table-owner privileges only through explicit role membership.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'whatplan_repository_admin') THEN
        EXECUTE 'CREATE ROLE whatplan_repository_admin '
            'NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION BYPASSRLS';
    ELSE
        EXECUTE 'ALTER ROLE whatplan_repository_admin '
            'NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION BYPASSRLS';
    END IF;
END
$$;

GRANT whatplan_migrator TO whatplan_repository_admin;
GRANT whatplan_repository_admin TO whatplan_repository_manager;
