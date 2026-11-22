# Withdrawal review demo

Run against the isolated `ptutor_withdrawal_demo` database after Flyway migration:

```powershell
python -m pip install "psycopg[binary]" cryptography
python scripts/withdrawal_demo/seed.py
```

The script adds fixtures only when missing and preserves decisions already made during testing.

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@ptutor.local` | `admin` |
| Accountant | `withdrawal.accountant@ptutor.local` | `Demo@2026` |
| Student | `withdrawal.student@ptutor.local` | `Demo@2026` |
| Tutor | `withdrawal.tutor@ptutor.local` | `Demo@2026` |

On a fresh database, the student has two `PENDING` requests (120,000 and 80,000 VND).
The tutor has one `PROCESSING` request (150,000 VND) for trying the QR and completion screen.
Running the seed again preserves statuses changed during testing.
Bank account numbers are fictitious; do not send real money to them.

For a fresh admin review exercise, create an empty `ptutor_withdrawal_review_demo`
database, let Flyway migrate it, then run:

```powershell
$env:PTUTOR_DEMO_DB = 'ptutor_withdrawal_review_demo'
$env:PTUTOR_DEMO_SCENARIO = 'admin_review'
python scripts/withdrawal_demo/seed.py
```

This creates two `PENDING` requests for each user. Their wallet balances already
account for the reserved amounts. Re-running the seed does not reset decisions.
