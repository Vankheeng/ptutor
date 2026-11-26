import type { Availability } from '../../types/api';

/** Slots whose end is not after their start cannot be sent; the API rejects them. */
export function hasInvalidSlot(slots: Availability[]): boolean {
  return slots.some((slot) => !slot.startTime || !slot.endTime || slot.endTime <= slot.startTime);
}
