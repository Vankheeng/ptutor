"""Seed isolated local withdrawal review fixtures; never resets reviewed requests."""

import base64
import hashlib
import hmac
import os
import re
import sys
import uuid
from datetime import datetime
from pathlib import Path

import psycopg
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


ROOT = Path(__file__).resolve().parents[2]
NAMESPACE = uuid.UUID("a70a4e94-fad2-46c8-b889-0a5185d102e0")


def fixture_id(name: str) -> uuid.UUID:
    return uuid.uuid5(NAMESPACE, name)


def settings() -> dict[str, str]:
    values = dict(line.split("=", 1) for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines()
                  if "=" in line and not line.startswith("#"))
    values.update(os.environ)
    return values


def encrypt(value: str, master_encoded: str, purpose: str) -> str:
    master = base64.b64decode(master_encoded)
    if len(master) != 32:
        raise ValueError("Encryption key must contain 32 bytes")
    key = hashlib.sha256(purpose.encode() + b"\0" + master).digest()
    iv = os.urandom(12)
    payload = iv + AESGCM(key).encrypt(iv, value.encode(), None)
    return base64.urlsafe_b64encode(payload).decode().rstrip("=")


def citizen_hash(value: str, master_encoded: str) -> str:
    master = base64.b64decode(master_encoded)
    key = hashlib.sha256(b"blind-index\0" + master).digest()
    return hmac.new(key, value.encode(), hashlib.sha256).hexdigest()


def main() -> int:
    values = settings()
    dbname = values.get("PTUTOR_DEMO_DB", "ptutor_withdrawal_demo")
    scenario = values.get("PTUTOR_DEMO_SCENARIO", "transfer")
    if scenario not in {"transfer", "admin_review"}:
        raise ValueError("PTUTOR_DEMO_SCENARIO must be transfer or admin_review")
    application = (ROOT / "backend/src/main/resources/application.yml").read_text(encoding="utf-8")
    configured_key = re.search(r"citizen-id-encryption-key:\s*(\S+)", application)
    citizen_key = values.get("APP_SECURITY_CITIZEN_ID_ENCRYPTION_KEY") or configured_key.group(1)
    bank_key = values.get("BANK_ACCOUNT_ENCRYPTION_KEY") or citizen_key
    accounts = [
        ("accountant", "withdrawal.accountant@ptutor.local", "Kế toán", "Demo", "099900000701"),
        ("student", "withdrawal.student@ptutor.local", "Học viên", "Demo", "099900000702"),
        ("tutor", "withdrawal.tutor@ptutor.local", "Gia sư", "Demo", "099900000703"),
    ]
    with psycopg.connect(host="localhost", port=5433, dbname=dbname,
                         user=values["DB_USERNAME"], password=values["DB_PASSWORD"]) as connection:
        with connection.cursor() as cursor:
            for role, email, first, last, citizen in accounts:
                cursor.execute("""
                    INSERT INTO users (id, citizen_id, citizen_id_hash, email, password, first_name, last_name, status)
                    VALUES (%s, %s, %s, %s, crypt('Demo@2026', gen_salt('bf', 10)), %s, %s, 'ACTIVE')
                    ON CONFLICT (email) DO UPDATE SET citizen_id = EXCLUDED.citizen_id,
                        citizen_id_hash = EXCLUDED.citizen_id_hash
                    WHERE users.citizen_id_hash IS DISTINCT FROM EXCLUDED.citizen_id_hash
                    """, (fixture_id(f"user:{role}"), encrypt(citizen, citizen_key, "encryption"),
                          citizen_hash(citizen, citizen_key), email, first, last))
            cursor.execute("""
                INSERT INTO employees (id, user_id, role, job_function)
                VALUES (%s, %s, 2, 'ACCOUNTANT') ON CONFLICT (id) DO NOTHING
                """, (fixture_id("employee:accountant"), fixture_id("user:accountant")))
            for role in ("student", "tutor"):
                table = "students" if role == "student" else "tutors"
                cursor.execute(f"INSERT INTO {table} (id, user_id) VALUES (%s, %s) ON CONFLICT (user_id) DO NOTHING",
                               (fixture_id(f"profile:{role}"), fixture_id(f"user:{role}")))

            if scenario == "admin_review":
                destinations = [
                    ("student", "MB", "MB Bank", "000000123456", "DEMO STUDENT", "500000.00", "200000.00"),
                    ("tutor", "VCB", "Vietcombank", "000000654321", "DEMO TUTOR", "600000.00", "240000.00"),
                ]
                requests = [
                    ("student-approve", "student", "120000.00", "PENDING", None),
                    ("student-reject", "student", "80000.00", "PENDING", None),
                    ("tutor-approve", "tutor", "150000.00", "PENDING", None),
                    ("tutor-reject", "tutor", "90000.00", "PENDING", None),
                ]
            else:
                destinations = [
                    ("student", "MB", "MB Bank", "000000123456", "DEMO STUDENT", "500000.00", "200000.00"),
                    ("tutor", "VCB", "Vietcombank", "000000654321", "DEMO TUTOR", "600000.00", "150000.00"),
                ]
                requests = [
                    ("student-approve", "student", "120000.00", "PENDING", None),
                    ("student-reject", "student", "80000.00", "PENDING", None),
                    ("tutor-transfer", "tutor", "150000.00", "PROCESSING", fixture_id("user:accountant")),
                ]
            for role, code, bank, number, holder, balance, pending in destinations:
                user_id = fixture_id(f"user:{role}")
                encrypted = encrypt(number, bank_key, "bank-account-number")
                cursor.execute("""
                    INSERT INTO wallets (id, user_id, balance, pending_balance)
                    VALUES (%s, %s, %s, %s) ON CONFLICT (user_id) DO NOTHING
                    """, (fixture_id(f"wallet:{role}"), user_id, balance, pending))
                cursor.execute("""
                    INSERT INTO bank_accounts (id, user_id, bank_code, bank_name, encrypted_account_number,
                                               account_number_last_four, account_holder_name)
                    VALUES (%s, %s, %s, %s, %s, %s, %s) ON CONFLICT (user_id) DO NOTHING
                    """, (fixture_id(f"bank:{role}"), user_id, code, bank, encrypted, number[-4:], holder))

            by_role = {row[0]: row for row in destinations}
            for name, role, amount, status, reviewer in requests:
                _, code, bank, number, holder, _, _ = by_role[role]
                cursor.execute("""
                    INSERT INTO withdrawal_requests (id, wallet_id, amount, bank_code, bank_name,
                                                     encrypted_account_number, account_number_last_four,
                                                     account_holder_name, note, idempotency_key, status,
                                                     reviewed_by_user_id, reviewed_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON CONFLICT (id) DO NOTHING
                    """, (fixture_id(f"request:{name}"), fixture_id(f"wallet:{role}"), amount,
                          code, bank, encrypt(number, bank_key, "bank-account-number"), number[-4:], holder,
                          f"DEMO withdrawal: {name}", f"withdrawal-demo:{name}", status, reviewer,
                          datetime.now() if reviewer else None))
    print(f"Seeded {dbname}: 1 accountant, 1 student, 1 tutor, {len(requests)} withdrawal requests")
    return 0


if __name__ == "__main__":
    sys.exit(main())
