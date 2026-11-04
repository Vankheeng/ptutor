import { useEffect, useState } from 'react';
import {
  getEmployeeOpenAssignments,
  getEmployees,
  type AdminEmployeeSummary,
  type EmployeeJobFunction,
  type EmployeeOpenAssignments
} from '../../../services/adminEmployeeApi';
import { reassignComplaint } from '../../../services/complaintApi';
import { reassignRefundRequest } from '../../../services/refundApi';
import { employeeErrorMessage } from '../employeeFormat';

export interface Loaded {
  assignments: EmployeeOpenAssignments;
  /** Active customer service employees, who may take over complaints. */
  complaintTargets: AdminEmployeeSummary[];
  /** Active accountants, who may take over refund requests. */
  refundTargets: AdminEmployeeSummary[];
}

async function load(employeeId: string, signal: AbortSignal): Promise<Loaded> {
  const assignments = await getEmployeeOpenAssignments(employeeId, signal);
  const targets = (jobFunction: EmployeeJobFunction, needed: boolean) =>
    needed
      ? getEmployees({ jobFunction, status: 'ACTIVE', page: 0 }, signal).then((page) =>
          page.content.filter((candidate) => candidate.employeeId !== employeeId)
        )
      : Promise.resolve([]);
  const [complaintTargets, refundTargets] = await Promise.all([
    targets('CUSTOMER_SERVICE', assignments.complaints.length > 0),
    targets('ACCOUNTANT', assignments.refundRequests.length > 0)
  ]);
  return { assignments, complaintTargets, refundTargets };
}

export type EmployeeHandover = ReturnType<typeof useEmployeeHandover>;

/**
 * Loads the complaints and refund requests an employee still has open, and moves them to the
 * colleagues the admin picks. The backend refuses to deactivate an employee or change their
 * job function until this work is handed over.
 */
export function useEmployeeHandover(employeeId: string, enabled: boolean) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [complaintTarget, setComplaintTarget] = useState('');
  const [refundTarget, setRefundTarget] = useState('');

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    load(employeeId, controller.signal)
      .then((result) => {
        setLoaded(result);
        setLoadError(null);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setLoadError(employeeErrorMessage(failure));
      });
    return () => controller.abort();
  }, [employeeId, enabled, revision]);

  const complaints = loaded?.assignments.complaints ?? [];
  const refunds = loaded?.assignments.refundRequests ?? [];
  const missingComplaintTarget = complaints.length > 0 && !complaintTarget;
  const missingRefundTarget = refunds.length > 0 && !refundTarget;
  const noComplaintCandidate = complaints.length > 0 && loaded?.complaintTargets.length === 0;
  const noRefundCandidate = refunds.length > 0 && loaded?.refundTargets.length === 0;

  return {
    loaded,
    loadError,
    loading: enabled && !loaded && !loadError,
    complaints,
    refunds,
    openCount: complaints.length + refunds.length,
    complaintTarget,
    setComplaintTarget,
    refundTarget,
    setRefundTarget,
    /** A target still has to be chosen. */
    incomplete: missingComplaintTarget || missingRefundTarget,
    /** Nobody active can take over the work, so the action cannot go ahead. */
    blocked: Boolean(noComplaintCandidate || noRefundCandidate),
    missingComplaintTarget,
    missingRefundTarget,
    noComplaintCandidate,
    noRefundCandidate,
    async transfer() {
      for (const complaint of complaints) await reassignComplaint(complaint.id, complaintTarget);
      for (const refund of refunds) await reassignRefundRequest(refund.id, refundTarget);
    },
    /** Reload after a failed transfer, since some items may already have moved. */
    reload() {
      setLoaded(null);
      setRevision((value) => value + 1);
    }
  };
}
