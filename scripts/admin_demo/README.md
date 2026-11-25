# Local admin demo data

Use this after starting PostgreSQL and loading the recommendation demo and evaluation data into `ptutor_recommendation_dev`. The runner needs Python's `cryptography` package to prepare encrypted citizen IDs for staff accounts.

```powershell
python scripts/admin_demo/seed.py
```

The script is repeatable. It adds fixtures for employee management, account states, tutor profile and certificate review, teaching and studying request review, complaints, notification campaigns, lessons, tuition payments, student wallet history, and refund review. Records are labelled `ADMIN DEMO` where the UI shows a title. Existing recommendation candidates and learned weights are left in place.

Local test accounts:

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@ptutor.local` | `admin` |
| Customer service | `admin.demo.cs@ptutor.local` | `Demo@2026` |
| Accountant | `admin.demo.accountant@ptutor.local` | `Demo@2026` |

The staff accounts let you verify menu permissions. The seeded pending rows let you try review actions. If you approve or reject a seeded row, running the script again will not reset that row's status; it keeps IDs stable and skips existing records.
