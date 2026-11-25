"""Export the migrated PTutor PostgreSQL schema as dbdiagram.io DBML.

Migration bookkeeping tables are excluded. Run against a database that has
both the Spring Flyway migrations and the assistant Alembic migrations.
"""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path

import psycopg


EXCLUDE = {("public", "flyway_schema_history"), ("assistant", "alembic_version")}


def clean_type(value: str) -> str:
    value = value.replace("character varying", "varchar")
    value = value.replace("timestamp without time zone", "timestamp")
    value = value.replace("timestamp with time zone", "timestamptz")
    value = value.replace("time without time zone", "time")
    value = value.replace("time with time zone", "timetz")
    value = value.replace("double precision", "double")
    value = value.replace("integer", "int")
    value = value.replace("boolean", "bool")
    # DBML requires array types in quoted identifiers; e.g. "varchar(20)[]".
    return f'"{value}"' if "[]" in value else value


def default_for_dbml(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = re.sub(r"::[\w .]+(?:\[\])?", "", value).strip()
    if stripped in {"true", "false"} or re.fullmatch(r"-?\d+(?:\.\d+)?", stripped):
        return stripped
    if re.fullmatch(r"'[^']*'", stripped):
        return stripped
    if value.upper() in {"CURRENT_TIMESTAMP", "NOW()"}:
        return "`now()`"
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", default="postgresql://postgres:postgres@127.0.0.1:5433/ptutor_report_admin")
    parser.add_argument("--output", default="report/diagrams/ptutor-current-schema.dbml")
    args = parser.parse_args()

    with psycopg.connect(args.dsn) as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT n.nspname, c.relname, a.attname,
                       pg_catalog.format_type(a.atttypid, a.atttypmod),
                       a.attnotnull, pg_get_expr(d.adbin, d.adrelid), a.attnum
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
                WHERE n.nspname IN ('public', 'assistant') AND c.relkind = 'r'
                ORDER BY n.nspname DESC, c.relname, a.attnum
            """)
            columns = cur.fetchall()
            cur.execute("""
                SELECT n.nspname, c.relname, x.contype, x.conname,
                       array_agg(a.attname ORDER BY k.ord)
                FROM pg_constraint x
                JOIN pg_class c ON c.oid=x.conrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace
                JOIN LATERAL unnest(x.conkey) WITH ORDINALITY k(attnum, ord) ON true
                JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=k.attnum
                WHERE n.nspname IN ('public','assistant') AND x.contype IN ('p','u')
                GROUP BY n.nspname,c.relname,x.contype,x.conname
                ORDER BY n.nspname DESC,c.relname,x.contype,x.conname
            """)
            keys = cur.fetchall()
            cur.execute("""
                SELECT ns.nspname, child.relname,
                       array_agg(ca.attname ORDER BY p.ord),
                       ps.nspname, parent.relname,
                       array_agg(pa.attname ORDER BY p.ord)
                FROM pg_constraint x
                JOIN pg_class child ON child.oid=x.conrelid
                JOIN pg_namespace ns ON ns.oid=child.relnamespace
                JOIN pg_class parent ON parent.oid=x.confrelid
                JOIN pg_namespace ps ON ps.oid=parent.relnamespace
                JOIN LATERAL unnest(x.conkey, x.confkey) WITH ORDINALITY p(child_num,parent_num,ord) ON true
                JOIN pg_attribute ca ON ca.attrelid=child.oid AND ca.attnum=p.child_num
                JOIN pg_attribute pa ON pa.attrelid=parent.oid AND pa.attnum=p.parent_num
                WHERE x.contype='f' AND ns.nspname IN ('public','assistant')
                GROUP BY ns.nspname,child.relname,ps.nspname,parent.relname,x.oid
                ORDER BY ns.nspname DESC,child.relname,ps.nspname,parent.relname
            """)
            refs = cur.fetchall()

    by_table: dict[tuple[str, str], list[tuple]] = defaultdict(list)
    for schema, table, name, type_name, not_null, default, position in columns:
        if (schema, table) not in EXCLUDE:
            by_table[(schema, table)].append((name, clean_type(type_name), not_null, default, position))

    single_keys: dict[tuple[str, str, str], set[str]] = defaultdict(set)
    compound_keys: dict[tuple[str, str], list[tuple[str, list[str]]]] = defaultdict(list)
    for schema, table, kind, _constraint, names in keys:
        if (schema, table) in EXCLUDE:
            continue
        if len(names) == 1:
            single_keys[(schema, table, names[0])].add("pk" if kind == "p" else "unique")
        else:
            compound_keys[(schema, table)].append(("pk" if kind == "p" else "unique", list(names)))

    lines = [
        "// PTutor database schema - exported from PostgreSQL after Flyway V53 and assistant Alembic migrations",
        "// Domain tables only. Migration history, check constraints and non-unique indexes are omitted.",
        "// Generated by scripts/report/generate_dbml.py. Do not edit the schema by changing this diagram.",
        "",
    ]
    for schema, table in sorted(by_table, key=lambda item: (item[0] != "public", item[1])):
        lines.append(f"Table {schema}.{table} {{")
        for name, type_name, not_null, default, _ in by_table[(schema, table)]:
            attributes = []
            attributes.extend(sorted(single_keys.get((schema, table, name), set())))
            if not_null and "pk" not in attributes:
                attributes.append("not null")
            normalized_default = default_for_dbml(default)
            if normalized_default is not None:
                attributes.append(f"default: {normalized_default}")
            suffix = f" [{', '.join(attributes)}]" if attributes else ""
            lines.append(f"  {name} {type_name}{suffix}")
        if compound_keys.get((schema, table)):
            lines.append("")
            lines.append("  indexes {")
            for kind, names in compound_keys[(schema, table)]:
                lines.append(f"    ({', '.join(names)}) [{kind}]")
            lines.append("  }")
        lines.append("}")
        lines.append("")

    for child_schema, child_table, child_columns, parent_schema, parent_table, parent_columns in refs:
        if (child_schema, child_table) in EXCLUDE or (parent_schema, parent_table) in EXCLUDE:
            continue
        child = ".".join((child_schema, child_table))
        parent = ".".join((parent_schema, parent_table))
        if len(child_columns) == 1:
            child += "." + child_columns[0]
            parent += "." + parent_columns[0]
        else:
            child += ".(" + ", ".join(child_columns) + ")"
            parent += ".(" + ", ".join(parent_columns) + ")"
        unique = any(set(child_columns) == set(names) for kind, names in compound_keys.get((child_schema, child_table), []) if kind in {"pk", "unique"})
        if len(child_columns) == 1:
            unique = unique or bool(single_keys.get((child_schema, child_table, child_columns[0]), set()) & {"pk", "unique"})
        lines.append(f"Ref: {child} {'-' if unique else '>'} {parent}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{output}: {len(by_table)} tables, {len(refs)} foreign keys")


if __name__ == "__main__":
    main()
