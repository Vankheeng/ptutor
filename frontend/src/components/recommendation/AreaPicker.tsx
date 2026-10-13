import { useEffect, useMemo, useState } from 'react';
import { getDistricts, getProvinces } from '../../services/locationApi';
import type { AreaFilter, AreaScope } from '../../services/recommendationApi';
import type { District, Province } from '../../types/api';

interface AreaPickerProps {
  /** Undefined means "not chosen": the server then uses the user's own district. */
  value: AreaFilter | undefined;
  onChange: (value: AreaFilter | undefined) => void;
  /** Province shown first when the user starts choosing, usually the one they live in. */
  homeProvinceId?: string | null;
}

const SCOPES: { scope: AreaScope | 'DEFAULT'; label: string }[] = [
  { scope: 'DEFAULT', label: 'Gần nơi tôi ở' },
  { scope: 'DISTRICTS', label: 'Chọn quận / phường' },
  { scope: 'PROVINCE', label: 'Toàn tỉnh / thành' },
  { scope: 'ANY', label: 'Không giới hạn' }
];

export function AreaPicker({ value, onChange, homeProvinceId }: AreaPickerProps) {
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [chosenProvinceId, setProvinceId] = useState<string>(value?.provinceId ?? '');
  // Until the user picks a province, the one they live in is shown.
  const provinceId = chosenProvinceId || homeProvinceId || '';
  const [keyword, setKeyword] = useState('');
  const scope = value?.scope ?? 'DEFAULT';
  const selected = useMemo(() => value?.districtIds ?? [], [value]);

  useEffect(() => {
    getProvinces()
      .then(setProvinces)
      .catch(() => setProvinces([]));
  }, []);

  useEffect(() => {
    if (!provinceId) return;
    let active = true;
    getDistricts(provinceId)
      .then((loaded) => {
        if (active) setDistricts(loaded);
      })
      .catch(() => {
        if (active) setDistricts([]);
      });
    return () => {
      active = false;
    };
  }, [provinceId]);

  const visible = useMemo(() => {
    const wanted = plain(keyword);
    return districts.filter((district) => !wanted || plain(district.name).includes(wanted));
  }, [districts, keyword]);

  const chooseScope = (next: AreaScope | 'DEFAULT') => {
    if (next === 'DEFAULT') onChange(undefined);
    else if (next === 'ANY') onChange({ scope: 'ANY' });
    else if (next === 'PROVINCE') onChange({ scope: 'PROVINCE', provinceId: provinceId || null });
    else onChange({ scope: 'DISTRICTS', districtIds: selected });
  };

  const toggleDistrict = (districtId: string) => {
    const next = selected.includes(districtId) ? selected.filter((id) => id !== districtId) : [...selected, districtId];
    onChange({ scope: 'DISTRICTS', districtIds: next });
  };

  const chooseProvince = (next: string) => {
    setProvinceId(next);
    if (scope === 'PROVINCE') onChange({ scope: 'PROVINCE', provinceId: next || null });
  };

  return (
    <div className="rec-area">
      <div className="rec-segmented" role="radiogroup" aria-label="Phạm vi khu vực">
        {SCOPES.map((option) => (
          <button
            key={option.scope}
            type="button"
            role="radio"
            aria-checked={scope === option.scope}
            className={scope === option.scope ? 'active' : ''}
            onClick={() => chooseScope(option.scope)}
          >
            {option.label}
          </button>
        ))}
      </div>
      {(scope === 'DISTRICTS' || scope === 'PROVINCE') && (
        <select value={provinceId} onChange={(event) => chooseProvince(event.target.value)} aria-label="Tỉnh / thành">
          <option value="">Chọn tỉnh / thành</option>
          {provinces.map((province) => (
            <option key={province.id} value={province.id}>
              {province.name}
            </option>
          ))}
        </select>
      )}
      {scope === 'DISTRICTS' && (
        <>
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tìm quận / phường"
            aria-label="Tìm quận / phường"
          />
          <div className="rec-checklist">
            {selected.length > 0 && (
              <small>
                Đã chọn {selected.length}
                {selected.some((id) => !districts.some((district) => district.id === id)) &&
                  ' (có nơi thuộc tỉnh / thành khác)'}
              </small>
            )}
            {visible.map((district) => (
              <label key={district.id}>
                <input
                  type="checkbox"
                  checked={selected.includes(district.id)}
                  onChange={() => toggleDistrict(district.id)}
                />
                {district.name}
              </label>
            ))}
            {!visible.length && <small>{provinceId ? 'Không có kết quả' : 'Chọn tỉnh / thành trước'}</small>}
          </div>
        </>
      )}
    </div>
  );
}

/** Lower case without accents, so "cau giay" finds "Cầu Giấy". */
function plain(value: string): string {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase().trim();
}
