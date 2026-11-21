# Admin contract demo data

Run on a local PTutor database that already contains completed contracts:

```powershell
Get-Content -Raw backend/demo/admin_contracts.sql | docker exec -i ptutor-postgres psql -v ON_ERROR_STOP=1 -U postgres -d ptutor
```

The script adds two **PENDING** and two **CANCELLED** contracts. Existing **ACTIVE** and **COMPLETED** records supply the other two filters. It uses fixed IDs, so rerunning it does not duplicate rows. It only inserts records.

After starting the updated backend and frontend, sign in as `admin@ptutor.local` and open `/admin/contracts`. The contract IDs can be verified with:

```powershell
docker exec ptutor-postgres psql -U postgres -d ptutor -c "SELECT id, status FROM contracts WHERE id IN (md5('admin-contract-demo-pending-1')::uuid, md5('admin-contract-demo-pending-2')::uuid, md5('admin-contract-demo-cancelled-1')::uuid, md5('admin-contract-demo-cancelled-2')::uuid);"
```
