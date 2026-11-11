import type { Discrepancy } from './api/types';

export const colors = {
  background: '#F2F4F7',
  surface: '#FFFFFF',
  text: '#101828',
  textMuted: '#475467',
  border: '#98A2B3',
  primary: '#0B4A8F',
  onPrimary: '#FFFFFF',
  danger: '#B42318',
  onDanger: '#FFFFFF',
  warningBg: '#FEF0C7',
  warningText: '#7A2E0E',
  offlineBg: '#1D2939',
  offlineText: '#FFFFFF',
  disabledBg: '#D0D5DD',
  disabledText: '#475467',
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 };

export const type = { small: 15, body: 17, title: 20, heading: 24 };

/** Sized for gloved hands. */
export const tapTarget = 56;

export const discrepancyStyles: Record<Discrepancy, { label: string; bg: string; text: string }> = {
  MATCH: { label: 'Match', bg: '#067647', text: '#FFFFFF' },
  SHORT: { label: 'Short', bg: '#B42318', text: '#FFFFFF' },
  OVER: { label: 'Over', bg: '#B54708', text: '#FFFFFF' },
  UNIT_MISMATCH: { label: 'Wrong unit', bg: '#6941C6', text: '#FFFFFF' },
  UNKNOWN_ITEM: { label: 'Not ordered', bg: '#344054', text: '#FFFFFF' },
  NOT_DELIVERED: { label: 'Not delivered', bg: '#7A271A', text: '#FFFFFF' },
};
