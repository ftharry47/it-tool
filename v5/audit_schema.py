import re
from pathlib import Path
from collections import defaultdict

BASE = Path(__file__).resolve().parent
ENTITY_DIR = BASE / 'src/main/java/com/alignedcardio/itsm/entity'
MIGRATION_DIR = BASE / 'src/main/resources/db/migration'

REQUIRED_BASE_COLS = {'id', 'org_id', 'created_at', 'updated_at', 'created_by', 'updated_by', 'deleted_at'}


def snake(name):
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()


def get_base_entity_tables():
    tables = {}
    for f in ENTITY_DIR.rglob('*.java'):
        text = f.read_text()
        if 'extends BaseEntity' not in text:
            continue
        cls_match = re.search(r'class\s+(\w+)\s+extends\s+BaseEntity', text)
        if not cls_match:
            continue
        cls = cls_match.group(1)
        table_match = re.search(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', text)
        table = table_match.group(1) if table_match else snake(cls)
        tables[table] = f.name
    return tables


def parse_migrations():
    table_cols = defaultdict(set)
    table_triggers = set()
    non_portable = []

    for f in sorted(MIGRATION_DIR.glob('V*.sql')):
        text = f.read_text()

        # CREATE TABLE ... ( ... );
        for m in re.finditer(
            r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?"?(\w+)"?\s*\((.*?)\)\s*;',
            text, re.DOTALL | re.IGNORECASE
        ):
            table = m.group(1).lower()
            body = m.group(2)
            for line in body.splitlines():
                line = line.strip()
                if not line:
                    continue
                if line.upper().startswith(('CONSTRAINT ', 'PRIMARY ', 'FOREIGN ', 'UNIQUE ', 'CHECK ', 'EXCLUDE ')):
                    continue
                col_match = re.match(r'"?(\w+)"?\s+\w', line)
                if col_match:
                    col = col_match.group(1).lower()
                    if col not in ('constraint', 'primary', 'foreign', 'unique', 'check', 'exclude'):
                        table_cols[table].add(col)

        # ALTER TABLE ... ADD COLUMN ...
        for m in re.finditer(
            r'ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?"?(\w+)"?\s+(.*?);',
            text, re.DOTALL | re.IGNORECASE
        ):
            table = m.group(1).lower()
            body = m.group(2)
            for ac in re.finditer(r'ADD\s+COLUMN(?:\s+IF\s+NOT\s+EXISTS)?\s+"?(\w+)"?', body, re.IGNORECASE):
                table_cols[table].add(ac.group(1).lower())

        # triggers for updated_at
        for m in re.finditer(
            r'CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(\w+)\s+.*?\s+ON\s+"?(\w+)"?',
            text, re.DOTALL | re.IGNORECASE
        ):
            table = m.group(2).lower()
            table_triggers.add(table)

        # non-portable / conditional DDL
        for m in re.finditer(r'ADD\s+CONSTRAINT\s+IF\s+NOT\s+EXISTS', text, re.IGNORECASE):
            non_portable.append((f.name, 'ADD CONSTRAINT IF NOT EXISTS', m.start()))
        for m in re.finditer(r'CREATE\s+OR\s+REPLACE\s+TRIGGER', text, re.IGNORECASE):
            non_portable.append((f.name, 'CREATE OR REPLACE TRIGGER', m.start()))

    return table_cols, table_triggers, non_portable


def col_sql(col):
    if col == 'id':
        return 'ADD COLUMN IF NOT EXISTS id UUID PRIMARY KEY'
    if col == 'org_id':
        return "ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid"
    if col == 'created_at':
        return 'ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now()'
    if col == 'updated_at':
        return 'ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now()'
    if col in ('created_by', 'updated_by'):
        return f'ADD COLUMN IF NOT EXISTS {col} UUID'
    if col == 'deleted_at':
        return 'ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ'
    return f'ADD COLUMN IF NOT EXISTS {col} TEXT'


def main():
    entities = get_base_entity_tables()
    table_cols, table_triggers, non_portable = parse_migrations()

    issues = []
    for table, cls in sorted(entities.items()):
        missing_cols = sorted(REQUIRED_BASE_COLS - table_cols.get(table, set()))
        missing_trigger = table not in table_triggers
        if missing_cols or missing_trigger:
            issues.append((table, cls, missing_cols, missing_trigger))

    report_path = BASE / 'schema_audit_report.md'
    with open(report_path, 'w') as out:
        out.write('# Schema Audit Report (V1–V19)\n\n')
        out.write('## BaseEntity Tables Missing Required Columns or `updated_at` Trigger\n\n')
        if not issues:
            out.write('No issues found.\n')
        else:
            out.write('| Table | Entity File | Missing BaseEntity Columns | Missing updated_at Trigger |\n')
            out.write('|---|---|---|---|\n')
            for table, cls, missing_cols, missing_trigger in issues:
                out.write(f'| {table} | {cls} | {", ".join(missing_cols) if missing_cols else "-"} | {"Yes" if missing_trigger else "No"} |\n')

        out.write('\n## Migrations with Non-Portable / Conditional DDL\n\n')
        if not non_portable:
            out.write('No issues found.\n')
        else:
            out.write('| Migration | Pattern | Offset |\n')
            out.write('|---|---|---|\n')
            for f, pat, pos in non_portable:
                out.write(f'| {f} | {pat} | {pos} |\n')

        out.write('\n## All BaseEntity Tables Mapped\n\n')
        for table, cls in sorted(entities.items()):
            out.write(f'- {table} ({cls})\n')

    v20_path = BASE / 'src/main/resources/db/migration/V20__base_entity_audit_gaps.sql'
    with open(v20_path, 'w') as out:
        out.write('-- Fix missing BaseEntity audit columns and updated_at triggers.\n')
        out.write('-- This migration is idempotent; it uses IF NOT EXISTS.\n\n')
        for table, cls, missing_cols, missing_trigger in sorted(issues):
            if missing_cols:
                clauses = [col_sql(c) for c in missing_cols]
                out.write(f'ALTER TABLE {table}\n    ' + ',\n    '.join(clauses) + ';\n')
            if missing_trigger:
                out.write(f'DROP TRIGGER IF EXISTS trg_{table}_updated_at ON {table};\n')
                out.write(f'CREATE TRIGGER trg_{table}_updated_at\n    BEFORE UPDATE ON {table}\n    FOR EACH ROW\n    EXECUTE FUNCTION set_updated_at();\n')
            out.write('\n')

    print(f'Report: {report_path}')
    print(f'V20 migration: {v20_path}')
    print(f'Tables mapped: {len(entities)}')
    print(f'Issues found: {len(issues)}')
    print(f'Non-portable patterns: {len(non_portable)}')


if __name__ == '__main__':
    main()
