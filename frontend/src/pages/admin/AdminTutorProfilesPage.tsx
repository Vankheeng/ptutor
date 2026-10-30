import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminTutors,
  type AccountStatus,
  type AdminTutorFilters,
  type TutorProfileStatus
} from '../../services/adminTutorApi';
import { accountStatusMeta, formatDate, formatNumber, fullName, profileStatusMeta } from './adminFormat';
import { AdminAvatar, AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { AdminReviewTabs } from './components/AdminReviewTabs';

type StatusTab = TutorProfileStatus | 'ALL';
const sortOptions = ['createdAt', 'recommendationScore', 'averageRating'] as const;
const reviewTabs: { value: StatusTab; label: string }[] = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'VERIFIED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'ALL', label: 'Tất cả' }
];

/** `review` is the profile review queue; `manage` lists every tutor under the Gia sư menu. */
type Mode = 'review' | 'manage';

function readTab(params: URLSearchParams, mode: Mode): StatusTab {
  return (params.get('status') as StatusTab | null) ?? (mode === 'review' ? 'PENDING' : 'ALL');
}

function readFilters(params: URLSearchParams, mode: Mode): AdminTutorFilters {
  const tab = readTab(params, mode);
  const number = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined);
  const sortBy = params.get('sortBy') as AdminTutorFilters['sortBy'] | null;
  return {
    profileStatus: tab === 'ALL' ? undefined : tab,
    keyword: params.get('keyword') ?? undefined,
    accountStatus: (params.get('accountStatus') as AccountStatus | null) ?? undefined,
    minRecommendationScore: number('minScore'),
    maxRecommendationScore: number('maxScore'),
    sortBy: sortBy && sortOptions.includes(sortBy) ? sortBy : 'createdAt',
    sortDirection: params.get('sortDirection') === 'ASC' ? 'ASC' : 'DESC',
    page: number('page') ?? 0,
    size: number('size') ?? 20
  };
}

export function AdminTutorProfilesPage({ mode = 'review' }: { mode?: Mode }) {
  const [params, setParams] = useSearchParams();
  const defaultTab: StatusTab = mode === 'review' ? 'PENDING' : 'ALL';
  const tab = readTab(params, mode);
  const filters = readFilters(params, mode);
  const basePath = mode === 'review' ? '/admin/tutor-profiles' : '/admin/tutors';
  const [formError, setFormError] = useState('');
  const queryKey = params.toString();

  const load = useCallback(
    (signal: AbortSignal) => getAdminTutors(readFilters(new URLSearchParams(queryKey), mode), signal),
    [mode, queryKey]
  );
  const { data, error, isLoading } = useResource(load);

  const update = (changes: Record<string, string | number | undefined>, keepPage = false) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    if (!keepPage) next.delete('page');
    setParams(next);
  };

  const submitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (key: string) => String(form.get(key) ?? '').trim();
    const min = text('minScore');
    const max = text('maxScore');
    if ((min && (Number(min) < 0 || Number(min) > 100)) || (max && (Number(max) < 0 || Number(max) > 100))) {
      setFormError('Điểm đề xuất phải nằm trong khoảng 0 đến 100.');
      return;
    }
    if (min && max && Number(min) > Number(max)) {
      setFormError('Điểm tối thiểu không được lớn hơn điểm tối đa.');
      return;
    }
    setFormError('');
    update({
      keyword: text('keyword'),
      accountStatus: text('accountStatus'),
      minScore: min,
      maxScore: max,
      sortBy: text('sortBy'),
      sortDirection: text('sortDirection')
    });
  };

  return (
    <div className="admin-page">
      {mode === 'review' ? <AdminReviewTabs /> : <h1 className="admin-title">Gia sư</h1>}
      <FilterChips<StatusTab>
        label="Trạng thái hồ sơ"
        value={tab}
        options={mode === 'review' ? reviewTabs : [reviewTabs[3], ...reviewTabs.slice(0, 3)]}
        onChange={(tab) => update({ status: tab === defaultTab ? undefined : tab })}
      />

      <section className="admin-card">
        <form key={queryKey} className="admin-filters" onSubmit={submitFilters}>
          <label className="admin-filter-grow">
            Từ khóa
            <input
              name="keyword"
              type="search"
              defaultValue={filters.keyword}
              placeholder="Tên, email, số điện thoại"
            />
          </label>
          <label>
            Tài khoản
            <select name="accountStatus" defaultValue={filters.accountStatus ?? ''}>
              <option value="">Tất cả</option>
              {(Object.keys(accountStatusMeta) as AccountStatus[]).map((status) => (
                <option key={status} value={status}>
                  {accountStatusMeta[status].label}
                </option>
              ))}
            </select>
          </label>
          <fieldset>
            <legend>Điểm đề xuất</legend>
            <div className="admin-range">
              <input
                name="minScore"
                type="number"
                min={0}
                max={100}
                step="0.01"
                aria-label="Điểm tối thiểu"
                placeholder="Từ"
                defaultValue={filters.minRecommendationScore}
              />
              <span aria-hidden="true">–</span>
              <input
                name="maxScore"
                type="number"
                min={0}
                max={100}
                step="0.01"
                aria-label="Điểm tối đa"
                placeholder="Đến"
                defaultValue={filters.maxRecommendationScore}
              />
            </div>
          </fieldset>
          <label>
            Sắp xếp theo
            <select name="sortBy" defaultValue={filters.sortBy}>
              <option value="createdAt">Ngày đăng ký</option>
              <option value="recommendationScore">Điểm đề xuất</option>
              <option value="averageRating">Đánh giá trung bình</option>
            </select>
          </label>
          <label>
            Thứ tự
            <select name="sortDirection" defaultValue={filters.sortDirection}>
              <option value="DESC">Giảm dần</option>
              <option value="ASC">Tăng dần</option>
            </select>
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Lọc
            </button>
            <button
              className="admin-button ghost"
              type="button"
              onClick={() => setParams(tab === defaultTab ? {} : { status: tab })}
            >
              Xóa bộ lọc
            </button>
          </div>
        </form>
        {formError && (
          <div className="admin-alert admin-alert-danger admin-card-alert" role="alert">
            {formError}
          </div>
        )}

        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có hồ sơ nào khớp bộ lọc." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Gia sư</th>
                  <th>Hồ sơ</th>
                  <th>Tài khoản</th>
                  <th>Đánh giá</th>
                  <th>Điểm đề xuất</th>
                  <th>Vi phạm</th>
                  <th>Ngày đăng ký</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((tutor) => {
                  const name = fullName(tutor.firstName, tutor.lastName, tutor.email);
                  const pending = mode === 'review' && tutor.profileStatus === 'PENDING';
                  return (
                    <tr key={tutor.userId}>
                      <td>
                        <div className="admin-person">
                          <AdminAvatar name={name} url={tutor.avatarUrl} />
                          <div>
                            <strong>{name}</strong>
                            <span>
                              {tutor.email}
                              {tutor.phone ? ` · ${tutor.phone}` : ''}
                            </span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <StatusBadge meta={profileStatusMeta[tutor.profileStatus]} />
                      </td>
                      <td>
                        <StatusBadge meta={accountStatusMeta[tutor.accountStatus]} />
                      </td>
                      <td>
                        {tutor.totalReviews ? `${formatNumber(tutor.averageRating, 1)} (${tutor.totalReviews})` : '—'}
                      </td>
                      <td className="admin-strong">{formatNumber(tutor.recommendationScore)}</td>
                      <td>{tutor.suspensionCount}</td>
                      <td>{formatDate(tutor.createdAt)}</td>
                      <td className="admin-cell-action">
                        <Link
                          className={`admin-button small${pending ? '' : ' secondary'}`}
                          to={`${basePath}/${tutor.userId}`}
                        >
                          {pending ? 'Xem & duyệt' : 'Xem chi tiết'}
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="admin-card-footer">
            <label className="admin-page-size">
              Hiển thị
              <select value={filters.size} onChange={(event) => update({ size: event.target.value })}>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
              / trang
            </label>
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit={mode === 'review' ? 'hồ sơ' : 'gia sư'}
              onChange={(page) => update({ page }, true)}
            />
          </div>
        )}
      </section>
    </div>
  );
}
