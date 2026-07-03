CREATE TABLE report_sources (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    internal_key VARCHAR(255) NOT NULL UNIQUE,
    table_number INTEGER NOT NULL
);

ALTER TABLE uploaded_files ADD COLUMN report_source_id INTEGER REFERENCES report_sources(id) ON DELETE SET NULL;

-- Migrate existing workspace or file names into report sources
INSERT INTO report_sources (name, internal_key, table_number)
SELECT 
    COALESCE(workspace, SPLIT_PART(file_name, '.', 1)) as name,
    UPPER(REGEXP_REPLACE(COALESCE(workspace, SPLIT_PART(file_name, '.', 1)), '[^a-zA-Z0-9_]', '_', 'g')) as internal_key,
    ROW_NUMBER() OVER (ORDER BY MIN(id)) as table_number
FROM uploaded_files
GROUP BY COALESCE(workspace, SPLIT_PART(file_name, '.', 1))
ON CONFLICT (name) DO NOTHING;

-- Link existing files to their respective report source
UPDATE uploaded_files f
SET report_source_id = s.id
FROM report_sources s
WHERE s.name = COALESCE(f.workspace, SPLIT_PART(f.file_name, '.', 1));
