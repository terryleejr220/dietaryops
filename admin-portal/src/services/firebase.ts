import { initializeApp, getApps } from 'firebase/app';
import { 
  getFirestore, 
  collection, 
  doc, 
  setDoc, 
  getDocs, 
  deleteDoc, 
  onSnapshot, 
  query, 
  orderBy, 
  limit, 
  Timestamp 
} from 'firebase/firestore';
import type { Company, StaffUser, ScanRecord } from '../types';

const firebaseConfig = {
  apiKey: "AIzaSyAHcz8JUXUU80ILDQ1LKizrix7jPaEHZk4",
  authDomain: "com-example-myapplicatio-46804.firebaseapp.com",
  projectId: "com-example-myapplicatio-46804",
  storageBucket: "com-example-myapplicatio-46804.firebasestorage.app",
  messagingSenderId: "1035289624449",
  appId: "1:1035289624449:web:adminportal"
};

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
export const db = getFirestore(app);

// In-memory fallback database
let memoryCompanies: Company[] = [
  {
    id: "DOPS",
    name: "DietaryOps Enterprise",
    code: "DOPS",
    spreadsheetId: "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY",
    webAppUrl: "https://script.google.com/macros/s/AKfycbx0heDYU0f1XyDELM_DFuKdlKmFW_ZJD6cEGegpLHva19PLv-_2CBE_U2EmAuJt1_FxDg/exec",
    departments: ["Dietary", "Housekeeping", "Nursing", "General Maintenance"],
    departmentTabs: {
      "Dietary": "Delivery Log",
      "Housekeeping": "Supply Receiving",
      "Nursing": "Clinical Supplies",
      "General Maintenance": "Facilities Log"
    },
    active: true
  }
];

let memoryUsers: Record<string, StaffUser[]> = {
  "DOPS": [{
    employeeId: "TL01",
    displayName: "Terry Little Jr.",
    companyCode: "DOPS",
    department: "Dietary",
    role: "ADMIN",
    pin: "1234",
    badgeToken: "CV-AUTH-8841",
    active: true
  }]
};

const withTimeout = <T>(promise: Promise<T>, ms: number = 2000): Promise<T> => {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error("Timeout")), ms))
  ]);
};

export const DEFAULT_COMPANIES = memoryCompanies;

export async function fetchCompanies(): Promise<Company[]> {
  try {
    const colRef = collection(db, 'companies');
    const snap = await withTimeout(getDocs(colRef));
    if (snap.empty) {
      await withTimeout(saveCompany(memoryCompanies[0]));
      return memoryCompanies;
    }
    return snap.docs.map(d => ({ id: d.id, ...d.data() } as Company));
  } catch (err: any) {
    console.warn("Using local fallback companies due to Firestore fetch error:", err);
    return memoryCompanies;
  }
}

export async function saveCompany(company: Company): Promise<void> {
  const code = company.code.trim().toUpperCase();
  const existingIdx = memoryCompanies.findIndex(c => c.code === code);
  if (existingIdx >= 0) {
    memoryCompanies[existingIdx] = { ...company, code, id: code };
  } else {
    memoryCompanies.push({ ...company, code, id: code });
  }

  try {
    const docRef = doc(db, 'companies', code);
    await withTimeout(setDoc(docRef, {
      ...company,
      code,
      id: code,
      updatedAt: Timestamp.now()
    }, { merge: true }));
  } catch (err: any) {
    console.warn("Saved company to local memory only:", err);
  }
}

export async function deleteCompany(companyCode: string): Promise<void> {
  const code = companyCode.trim().toUpperCase();
  memoryCompanies = memoryCompanies.filter(c => c.code !== code);
  try {
    await withTimeout(deleteDoc(doc(db, 'companies', code)));
  } catch (err) {
    console.warn("Deleted company from local memory only:", err);
  }
}

export async function fetchStaffUsers(companyCode: string): Promise<StaffUser[]> {
  const code = companyCode.trim().toUpperCase();
  if (!memoryUsers[code]) memoryUsers[code] = [];
  
  try {
    const usersCol = collection(db, 'companies', code, 'users');
    const snap = await withTimeout(getDocs(usersCol));
    if (snap.empty && (code === 'DOPS' || code === 'MAIN')) {
      const defaultUser: StaffUser = {
        employeeId: "TL01",
        displayName: "Terry Little Jr.",
        companyCode: code,
        department: "Dietary",
        role: "ADMIN",
        pin: "1234",
        badgeToken: "DOPS-AUTH-8841",
        active: true
      };
      await withTimeout(saveStaffUser(code, defaultUser));
      return memoryUsers[code];
    }
    return snap.docs.map(d => ({ employeeId: d.id, ...d.data() } as StaffUser));
  } catch (err) {
    console.warn("Using fallback staff users due to error:", err);
    return memoryUsers[code] || [];
  }
}

export async function saveStaffUser(companyCode: string, user: StaffUser): Promise<void> {
  const code = companyCode.trim().toUpperCase();
  const empId = user.employeeId.trim().toUpperCase();
  
  if (!memoryUsers[code]) memoryUsers[code] = [];
  const existingIdx = memoryUsers[code].findIndex(u => u.employeeId === empId);
  const userToSave = {
    ...user,
    employeeId: empId,
    companyCode: code,
    badgeToken: user.badgeToken || `TKN-${Math.floor(100000 + Math.random() * 900000)}`
  };
  
  if (existingIdx >= 0) {
    memoryUsers[code][existingIdx] = userToSave;
  } else {
    memoryUsers[code].push(userToSave);
  }

  try {
    const userRef = doc(db, 'companies', code, 'users', empId);
    await withTimeout(setDoc(userRef, {
      ...userToSave,
      updatedAt: Timestamp.now()
    }, { merge: true }));
  } catch (err) {
    console.warn("Saved user to local memory only:", err);
  }
}

export async function deleteStaffUser(companyCode: string, employeeId: string): Promise<void> {
  const code = companyCode.trim().toUpperCase();
  const empId = employeeId.trim().toUpperCase();
  
  if (memoryUsers[code]) {
    memoryUsers[code] = memoryUsers[code].filter(u => u.employeeId !== empId);
  }
  
  try {
    await withTimeout(deleteDoc(doc(db, 'companies', code, 'users', empId)));
  } catch (err) {
    console.warn("Deleted user from local memory only:", err);
  }
}

export function subscribeLiveScans(companyCode: string, onUpdate: (scans: ScanRecord[]) => void): () => void {
  const code = companyCode.trim().toUpperCase();
  try {
    const logsCol = collection(db, 'companies', code, 'scan_logs');
    const q = query(logsCol, orderBy('scanTimestamp', 'desc'), limit(50));
    return onSnapshot(q, (snapshot) => {
      const records = snapshot.docs.map(d => ({ id: d.id, ...d.data() } as ScanRecord));
      onUpdate(records);
    }, (error) => {
      console.warn("Live scan subscription warning:", error);
    });
  } catch {
    return () => {};
  }
}
