export type StaffRole = 'OPERATOR' | 'SUPERVISOR' | 'ADMIN' | 'DEPT_ADMIN' | 'SUPER_ADMIN';

export interface Company {
  id: string;
  name: string;
  code: string;
  spreadsheetId: string;
  webAppUrl: string;
  departments: string[];
  departmentTabs: Record<string, string>;
  active: boolean;
  createdAt?: number;
}

export interface StaffUser {
  employeeId: string;
  displayName: string;
  companyCode: string;
  department: string;
  role: StaffRole;
  pin: string;
  badgeToken: string;
  active: boolean;
}

export interface ScanRecord {
  id: string;
  syscoUpc: string;
  itemName: string;
  deliveryDate: string;
  useByDate: string;
  category: string;
  shelfLifeDays: number;
  unit: string;
  onHandAmount: number;
  scanTimestamp: number;
  printed: boolean;
  syncedToSheets: boolean;
  isAudit: boolean;
  receivedBy?: string;
}
