CREATE SCHEMA IF NOT EXISTS patentflow AUTHORIZATION patentflow;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
ALTER ROLE patentflow IN DATABASE patentflow SET search_path TO patentflow, public;
