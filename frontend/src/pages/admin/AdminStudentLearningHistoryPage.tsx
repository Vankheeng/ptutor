import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getAdminStudentLearningHistory, getAdminStudentLesson } from '../../services/adminStudentApi';
import type { ContractStatus, LessonStatus } from '../../services/adminTutorApi';
import { contractStatusMeta, formatDate, formatTime, lessonStatusMeta, teachingModeLabels } from './adminFormat';
import { useAdminStudent } from './adminStudentContext';
import { AdminModal, AdminPagination, AdminState, DefinitionGrid, StatusBadge } from './components/AdminUi';

export function AdminStudentLearningHistoryPage() {
  const { account } = useAdminStudent();
  const [lessonStatus, setLessonStatus] = useState<LessonStatus | ''>('');
  const [contractStatus, setContractStatus] = useState<ContractStatus | ''>('');
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminStudentLearningHistory(
        account.userId,
        { lessonStatus: lessonStatus || undefined, contractStatus: contractStatus || undefined, page },
        signal
      ),
    [account.userId, contractStatus, lessonStatus, page]
  );
  const { data, error, isLoading } = useResource(load);
  const summary = data?.summary;

  return (
    <>
      {summary && (
        <section className="admin-stats" aria-label="Thống kê buổi học">
          {[
            ['Tổng buổi', summary.total, ''],
            ['Đã lên lịch', summary.scheduled, 'info'],
            ['Chờ xác nhận', summary.pendingConfirmation, 'pending'],
            ['Đã xác nhận', summary.confirmed, 'brand'],
            ['Hoàn thành', summary.completed, 'success'],
            ['Đã hủy', summary.cancelled, 'neutral']
          ].map(([label, value, tone]) => (
            <div key={label} className={`admin-stat admin-stat-${tone || 'default'}`}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </section>
      )}
      <section className="admin-card">
        <div className="admin-filters">
          <label>
            Trạng thái buổi học
            <select
              value={lessonStatus}
              onChange={(event) => {
                setLessonStatus(event.target.value as LessonStatus | '');
                setPage(0);
              }}
            >
              <option value="">Tất cả</option>
              {(Object.keys(lessonStatusMeta) as LessonStatus[]).map((value) => (
                <option key={value} value={value}>
                  {lessonStatusMeta[value].label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Trạng thái hợp đồng
            <select
              value={contractStatus}
              onChange={(event) => {
                setContractStatus(event.target.value as ContractStatus | '');
                setPage(0);
              }}
            >
              <option value="">Tất cả</option>
              {(Object.keys(contractStatusMeta) as ContractStatus[]).map((value) => (
                <option key={value} value={value}>
                  {contractStatusMeta[value].label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.lessons.content.length === 0 ? (
          <AdminState empty="Chưa có buổi học nào." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Buổi học</th>
                  <th>Gia sư</th>
                  <th>Ngày · Giờ</th>
                  <th>Hình thức</th>
                  <th>Trạng thái</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.lessons.content.map((lesson) => (
                  <tr key={lesson.lessonId}>
                    <td>
                      <div className="admin-stack">
                        <strong>{lesson.title || 'Buổi học'}</strong>
                        <span>
                          {lesson.subjectName} · {lesson.gradeName}
                        </span>
                      </div>
                    </td>
                    <td>{lesson.tutorName}</td>
                    <td className="admin-nowrap">
                      {formatDate(lesson.date)} · {formatTime(lesson.startTime)}–{formatTime(lesson.endTime)}
                    </td>
                    <td>{teachingModeLabels[lesson.teachingMode]}</td>
                    <td>
                      <StatusBadge meta={lessonStatusMeta[lesson.status]} />
                    </td>
                    <td className="admin-cell-action">
                      <button
                        className="admin-button small secondary"
                        type="button"
                        onClick={() => setOpenId(lesson.lessonId)}
                      >
                        Chi tiết
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="admin-card-footer">
            <AdminPagination
              page={data.lessons.page}
              totalPages={data.lessons.totalPages}
              totalElements={data.lessons.totalElements}
              unit="buổi học"
              onChange={setPage}
            />
          </div>
        )}
      </section>
      {openId && <LessonModal userId={account.userId} lessonId={openId} onClose={() => setOpenId(null)} />}
    </>
  );
}

function LessonModal({ userId, lessonId, onClose }: { userId: string; lessonId: string; onClose: () => void }) {
  const load = useCallback(
    (signal: AbortSignal) => getAdminStudentLesson(userId, lessonId, signal),
    [lessonId, userId]
  );
  const { data: lesson, error, isLoading } = useResource(load);

  return (
    <AdminModal
      title={lesson?.title || 'Buổi học'}
      subtitle={lesson && <StatusBadge meta={lessonStatusMeta[lesson.status]} />}
      onClose={onClose}
    >
      <div className="admin-modal-pad">
        {!lesson ? (
          <AdminState loading={isLoading} error={error} />
        ) : (
          <DefinitionGrid
            columns={3}
            items={[
              ['Gia sư', lesson.tutorName],
              ['Email gia sư', lesson.tutorEmail],
              ['Môn · Lớp', `${lesson.subjectName} · ${lesson.gradeName}`],
              ['Ngày', formatDate(lesson.date)],
              ['Giờ', `${formatTime(lesson.startTime)}–${formatTime(lesson.endTime)}`],
              ['Hình thức', teachingModeLabels[lesson.teachingMode]],
              ['Hợp đồng', <StatusBadge key="contract" meta={contractStatusMeta[lesson.contractStatus]} />],
              ['Ghi chú', lesson.note || '—']
            ]}
          />
        )}
      </div>
    </AdminModal>
  );
}
