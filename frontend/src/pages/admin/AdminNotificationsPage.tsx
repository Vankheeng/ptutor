import { useCallback, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getNotificationCampaigns,
  type NotificationAudience,
  type NotificationCampaignStatus,
  type NotificationCategory
} from '../../services/adminNotificationApi';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { formatDateTime } from './adminFormat';
import {
  audienceLabels,
  categoryLabels,
  notificationAudiences,
  notificationCategories,
  notificationStatuses,
  notificationStatusMeta,
  notificationTime,
  readRate
} from './notificationFormat';

type StatusFilter = NotificationCampaignStatus | 'ALL';

const statusFilters: StatusFilter[] = ['ALL', ...notificationStatuses];

function pick<T extends string>(options: readonly T[], value: string | null) {
  return options.find((option) => option === value);
}

export function AdminNotificationsPage() {
  const [params, setParams] = useSearchParams();
  const status = pick(statusFilters, params.get('status')) ?? 'ALL';
  const audience = pick<NotificationAudience>(notificationAudiences, params.get('audience'));
  const category = pick<NotificationCategory>(notificationCategories, params.get('category'));
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);

  const load = useCallback(
    (signal: AbortSignal) =>
      getNotificationCampaigns(
        {
          status: status === 'ALL' ? undefined : status,
          audience,
          category,
          keyword: keyword || undefined,
          page
        },
        signal
      ),
    [audience, category, keyword, page, status]
  );
  const { data, error, isLoading } = useResource(load);

  const update = (changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    setParams(next);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    update({
      keyword: String(form.get('keyword') ?? '').trim(),
      audience: String(form.get('audience') ?? ''),
      category: String(form.get('category') ?? ''),
      page: undefined
    });
  };

  return (
    <div className="admin-page">
      <div className="admin-section-heading">
        <h1 className="admin-title">Thông báo</h1>
        <Link className="admin-button" to="/admin/notifications/new">
          Tạo thông báo
        </Link>
      </div>
      <FilterChips<StatusFilter>
        label="Trạng thái thông báo"
        value={status}
        options={statusFilters.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : notificationStatusMeta[value].label
        }))}
        onChange={(value) => update({ status: value === 'ALL' ? undefined : value, page: undefined })}
      />
      <section className="admin-card">
        <form key={params.toString()} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder="Tiêu đề hoặc nội dung" />
          </label>
          <label>
            Đối tượng
            <select name="audience" defaultValue={audience ?? ''}>
              <option value="">Tất cả đối tượng</option>
              {notificationAudiences.map((value) => (
                <option key={value} value={value}>
                  {audienceLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Loại
            <select name="category" defaultValue={category ?? ''}>
              <option value="">Tất cả loại</option>
              {notificationCategories.map((value) => (
                <option key={value} value={value}>
                  {categoryLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Tìm
            </button>
          </div>
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có thông báo phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table admin-notification-table">
              <colgroup>
                <col />
                <col className="admin-notification-col-category" />
                <col className="admin-notification-col-audience" />
                <col className="admin-notification-col-status" />
                <col className="admin-notification-col-time" />
                <col className="admin-notification-col-read" />
                <col className="admin-notification-col-action" />
              </colgroup>
              <thead>
                <tr>
                  <th>Thông báo</th>
                  <th>Loại</th>
                  <th>Đối tượng</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                  <th>Đã đọc</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((campaign) => {
                  const time = notificationTime(campaign);
                  return (
                    <tr key={campaign.id}>
                      <td>
                        <div className="admin-stack admin-notification-cell">
                          <strong>{campaign.title}</strong>
                          <span>{campaign.content}</span>
                        </div>
                      </td>
                      <td className="admin-nowrap">{categoryLabels[campaign.category]}</td>
                      <td className="admin-nowrap">{audienceLabels[campaign.audience]}</td>
                      <td>
                        <StatusBadge meta={notificationStatusMeta[campaign.status]} />
                      </td>
                      <td className="admin-nowrap">
                        <div className="admin-stack">
                          <div>{formatDateTime(time.value)}</div>
                          <span>{time.label}</span>
                        </div>
                      </td>
                      <td>
                        {campaign.status === 'SENT' ? (
                          <div className="admin-notification-read">
                            <div className="admin-meter" aria-hidden="true">
                              <span style={{ width: `${readRate(campaign.readCount, campaign.recipientCount)}%` }} />
                            </div>
                            <span>
                              {campaign.readCount.toLocaleString('vi-VN')} /{' '}
                              {campaign.recipientCount.toLocaleString('vi-VN')}
                            </span>
                          </div>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="admin-cell-action">
                        <Link className="admin-button small secondary" to={`/admin/notifications/${campaign.id}`}>
                          Xem
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
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit="thông báo"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
    </div>
  );
}
