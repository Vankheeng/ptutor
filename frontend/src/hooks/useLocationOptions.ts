import { useEffect, useState } from 'react';
import { getDistricts, getProvinces } from '../services/locationApi';
import type { District, Province } from '../types/api';

export function useLocationOptions(provinceId: string) {
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districtResult, setDistrictResult] = useState<{ provinceId: string; items: District[] } | null>(null);
  const districts = districtResult?.provinceId === provinceId ? districtResult.items : [];

  useEffect(() => {
    let active = true;
    getProvinces()
      .then((items) => {
        if (active) setProvinces(items);
      })
      .catch(() => {
        if (active) setProvinces([]);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (!provinceId) return;
    getDistricts(provinceId)
      .then((items) => {
        if (active) setDistrictResult({ provinceId, items });
      })
      .catch(() => {
        if (active) setDistrictResult({ provinceId, items: [] });
      });
    return () => {
      active = false;
    };
  }, [provinceId]);

  return { provinces, districts };
}
