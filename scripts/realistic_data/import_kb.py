"""Copy only the assistant knowledge base from one local Docker database to another.

Run after the target has the assistant Alembic schema. The target KB must be empty.
Conversations, messages, feedback, and user data are never exported.
"""

import argparse
import subprocess
import sys

TABLES = (
    "assistant.kb_document",
    "assistant.kb_document_version",
    "assistant.kb_chunk",
    "assistant.kb_setting",
)


def run(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True)
    if result.returncode:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
        raise SystemExit(result.returncode)
    return result.stdout


def count(container, database, table):
    output = run(["docker", "exec", "-u", "postgres", container,
                  "psql", "-X", "-At", "-d", database, "-c", f"SELECT count(*) FROM {table}"])
    return int(output.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="ptutor")
    parser.add_argument("--target", default="ptutor_realistic_clean")
    parser.add_argument("--container", default="ptutor-postgres")
    args = parser.parse_args()
    if args.source == args.target or args.target in ("ptutor", "postgres"):
        parser.error("source and target must differ; choose an isolated target database")
    if count(args.container, args.target, TABLES[0]):
        parser.error("target knowledge base is not empty")
    dump = run(["docker", "exec", "-u", "postgres", args.container,
                "pg_dump", "--data-only", "--column-inserts", "--no-owner", "--no-privileges",
                *[f"--table={table}" for table in TABLES], "-d", args.source])
    # kb_document.active_version_id points to kb_document_version, which points back
    # to kb_document. Disabling FK triggers within this isolated transaction lets
    # the circular pair load; post-load checks below ensure the links are valid.
    sql = b"BEGIN;\nSET LOCAL session_replication_role = replica;\n" + dump + b"\nCOMMIT;\n"
    run(["docker", "exec", "-i", "-u", "postgres", args.container,
         "psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", args.target], sql)
    source_counts = [count(args.container, args.source, table) for table in TABLES]
    target_counts = [count(args.container, args.target, table) for table in TABLES]
    if source_counts != target_counts:
        raise SystemExit(f"count mismatch: source={source_counts}, target={target_counts}")
    invalid = run(["docker", "exec", "-u", "postgres", args.container,
                   "psql", "-X", "-At", "-d", args.target, "-c", """
                   SELECT (SELECT count(*) FROM assistant.kb_document d
                           WHERE d.active_version_id IS NOT NULL AND NOT EXISTS
                           (SELECT 1 FROM assistant.kb_document_version v WHERE v.id=d.active_version_id))
                        + (SELECT count(*) FROM assistant.kb_document_version v WHERE NOT EXISTS
                           (SELECT 1 FROM assistant.kb_document d WHERE d.id=v.document_id))
                        + (SELECT count(*) FROM assistant.kb_chunk c WHERE NOT EXISTS
                           (SELECT 1 FROM assistant.kb_document_version v WHERE v.id=c.version_id))
                   """])
    if int(invalid.strip()):
        raise SystemExit("knowledge-base foreign-key validation failed")
    print(f"Copied {target_counts[0]} documents and {target_counts[2]} chunks to {args.target}.")


if __name__ == "__main__":
    main()
