-- Extensions required by the application
-- uuid-ossp: UUID generation
-- vector: pgvector for embedding storage (RAG)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;