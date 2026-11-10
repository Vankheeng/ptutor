import { useEffect, useState } from 'react';
import { getDistricts, getProvinces } from '../services/locationApi';
import type { District, Province } from '../types/api';

export function useLocationOptions(provinceId: string) {
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);

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
    if (!provinceId) {
      setDistricts([]);
      return () => {
        active = false;
      };
    }
    getDistricts(provinceId)
      .then((items) => {
        if (active) setDistricts(items);
      })
      .catch(() => {
        if (active) setDistricts([]);
      });
    return () => {
      active = false;
    };
  }, [provinceId]);

  return { provinces, districts };
}
