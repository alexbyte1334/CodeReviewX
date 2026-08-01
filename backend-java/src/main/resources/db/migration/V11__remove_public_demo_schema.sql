-- The clean self-hosted baseline has no fixed public Demo runtime. The V8
-- migration remains immutable for Flyway history; fresh databases remove its
-- Demo-only tables here before the application starts.
DROP TABLE IF EXISTS review_run_event;
DROP TABLE IF EXISTS review_execution_job;
DROP TABLE IF EXISTS demo_request_bucket;
DROP TABLE IF EXISTS demo_run;
