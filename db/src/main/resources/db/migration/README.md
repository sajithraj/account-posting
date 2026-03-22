# DB Migration Scripts

Naming convention: `V{version}__{description}.sql`

Examples:
- `V1__init_schema.sql`
- `V2__add_account_table.sql`
- `V3__add_index_on_status.sql`

Rules:
- Never modify an existing migration that has already been applied.
- Use repeatable migrations (`R__`) only for views, functions, and stored procedures.
- Always use `TIMESTAMP WITH TIME ZONE` for timestamp columns.
- Include `created_at` / `updated_at` on every table.
