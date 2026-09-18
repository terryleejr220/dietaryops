import React, { useState, useEffect } from 'react';
import { 
  Building2, 
  Users, 
  Radio, 
  Sheet, 
  ExternalLink, 
  RefreshCw,
  QrCode
} from 'lucide-react';
import type { Company, StaffUser } from './types';
import { 
  fetchCompanies, 
  saveCompany, 
  deleteCompany, 
  fetchStaffUsers, 
  saveStaffUser, 
  deleteStaffUser 
} from './services/firebase';
import { CompanyManager } from './components/CompanyManager';
import { StaffRoster } from './components/StaffRoster';
import { LiveScanFeed } from './components/LiveScanFeed';
import { LoginScreen } from './components/LoginScreen';

type NavTab = 'COMPANIES' | 'STAFF' | 'LIVE_FEED' | 'SHEETS_SYNC';

export const App: React.FC = () => {
  const [loggedInUser, setLoggedInUser] = useState<StaffUser | null>(null);
  const [activeTab, setActiveTab] = useState<NavTab>('STAFF');
  const [companies, setCompanies] = useState<Company[]>([]);
  const [selectedCompany, setSelectedCompany] = useState<Company | null>(null);
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const timeout = (ms: number) => new Promise<never>((_, reject) => setTimeout(() => reject(new Error("Timeout")), ms));
      
      const comps = await Promise.race([fetchCompanies(), timeout(3000)]);
      setCompanies(comps);
      if (comps.length > 0) {
        const current = selectedCompany 
          ? (comps.find(c => c.code === selectedCompany.code) || comps[0]) 
          : comps[0];
        setSelectedCompany(current);
        const users = await Promise.race([fetchStaffUsers(current.code), timeout(3000)]);
        setStaffUsers(users);
      }
    } catch (err) {
      console.warn("Timeout or error loading data, using fallbacks:", err);
      // Fallback
      import('./services/firebase').then(fb => {
        setCompanies(fb.DEFAULT_COMPANIES);
        setSelectedCompany(fb.DEFAULT_COMPANIES[0]);
        setStaffUsers([{
          employeeId: "TL01",
          displayName: "Terry Little Jr.",
          companyCode: "DOPS",
          department: "Dietary",
          role: "ADMIN",
          pin: "1234",
          badgeToken: "CV-AUTH-8841",
          active: true
        }]);
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSelectCompany = async (comp: Company) => {
    setSelectedCompany(comp);
    setIsRefreshing(true);
    const users = await fetchStaffUsers(comp.code);
    setStaffUsers(users);
    setIsRefreshing(false);
  };

  const handleSaveCompany = async (company: Company) => {
    await saveCompany(company);
    await loadData();
  };

  const handleDeleteCompany = async (code: string) => {
    await deleteCompany(code);
    await loadData();
  };

  const handleSaveUser = async (user: StaffUser) => {
    if (!selectedCompany) return;
    await saveStaffUser(selectedCompany.code, user);
    const users = await fetchStaffUsers(selectedCompany.code);
    setStaffUsers(users);
  };

  const handleDeleteUser = async (empId: string) => {
    if (!selectedCompany) return;
    await deleteStaffUser(selectedCompany.code, empId);
    const users = await fetchStaffUsers(selectedCompany.code);
    setStaffUsers(users);
  };

  if (isLoading && companies.length === 0) {
    return (
      <div className="min-h-screen bg-[#0A1016] text-white flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <QrCode className="w-12 h-12 text-teal-500 animate-pulse" />
          <p className="text-slate-400 font-mono text-sm">Connecting to Firestore & Telemetry...</p>
        </div>
      </div>
    );
  }

  if (!loggedInUser) {
    return <LoginScreen companies={companies} onLogin={(u, c) => {
      setLoggedInUser(u);
      setSelectedCompany(c);
      setActiveTab('STAFF');
      fetchStaffUsers(c.code).then(setStaffUsers);
    }} />;
  }

  const isSuperAdmin = loggedInUser.role === 'SUPER_ADMIN';

  return (
    <div className="min-h-screen bg-[#0A1016] text-slate-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="bg-[#101720]/90 backdrop-blur-md border-b border-slate-800/80 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          {/* Logo & Brand */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-teal-600 via-cyan-600 to-teal-400 flex items-center justify-center shadow-lg shadow-teal-500/20 text-white font-bold">
              <QrCode className="w-5 h-5" />
            </div>
            <div>
              <div className="text-base font-extrabold text-white flex items-center gap-2">
                DietaryOps Manager
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-teal-500/15 text-teal-300 font-mono font-semibold border border-teal-500/30">
                  {isSuperAdmin ? 'SUPER-ADMIN CONSOLE' : 'DEPT-ADMIN CONSOLE'}
                </span>
              </div>
              <div className="text-[11px] text-slate-400">
                Multi-Tenant Food Service & Clinical Inventory Control
              </div>
            </div>
          </div>

          {/* Active Store Context Selector */}
          {selectedCompany && (
            <div className="flex items-center gap-3">
              <div className="hidden sm:flex items-center gap-2 bg-[#0D141B] border border-slate-800 rounded-xl px-3 py-1.5 text-xs">
                <span className="text-slate-400">Facility Context:</span>
                {isSuperAdmin ? (
                  <select
                    value={selectedCompany.code}
                    onChange={(e) => {
                      const found = companies.find(c => c.code === e.target.value);
                      if (found) handleSelectCompany(found);
                    }}
                    className="bg-transparent text-teal-300 font-bold focus:outline-none cursor-pointer"
                  >
                    {companies.map(c => (
                      <option key={c.code} value={c.code} className="bg-[#131B24] text-white">
                        {c.name} ({c.code})
                      </option>
                    ))}
                  </select>
                ) : (
                  <span className="text-teal-300 font-bold">{selectedCompany.name}</span>
                )}
              </div>

              <button
                onClick={loadData}
                disabled={isRefreshing}
                className="p-2 rounded-xl bg-[#0D141B] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition-colors cursor-pointer"
                title="Refresh Data"
              >
                <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-teal-400' : ''}`} />
              </button>
            </div>
          )}
        </div>

        {/* Tab Navigation */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-2 -mb-px">
          <button
            onClick={() => setActiveTab('STAFF')}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition-all cursor-pointer ${
              activeTab === 'STAFF'
                ? 'border-teal-500 text-teal-400 bg-teal-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Users className="w-4 h-4" />
            Staff & Badges
            {selectedCompany && (
              <span className="text-xs px-1.5 py-0.2 rounded-full bg-slate-800 text-slate-400">
                {staffUsers.length}
              </span>
            )}
          </button>

          {isSuperAdmin && (
            <button
              onClick={() => setActiveTab('COMPANIES')}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition-all cursor-pointer ${
                activeTab === 'COMPANIES'
                  ? 'border-teal-500 text-teal-400 bg-teal-500/5'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              <Building2 className="w-4 h-4" />
              Stores & Facilities
              <span className="text-xs px-1.5 py-0.2 rounded-full bg-slate-800 text-slate-400">
                {companies.length}
              </span>
            </button>
          )}

          <button
            onClick={() => setActiveTab('LIVE_FEED')}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition-all cursor-pointer ${
              activeTab === 'LIVE_FEED'
                ? 'border-teal-500 text-teal-400 bg-teal-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Radio className="w-4 h-4 text-emerald-400 animate-pulse" />
            Live Kitchen Feed
          </button>

          <button
            onClick={() => setActiveTab('SHEETS_SYNC')}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition-all cursor-pointer ${
              activeTab === 'SHEETS_SYNC'
                ? 'border-teal-500 text-teal-400 bg-teal-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Sheet className="w-4 h-4 text-emerald-400" />
            Dedicated Spreadsheets
          </button>
        </div>
      </header>

      {/* Main Content Body */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400">
            <RefreshCw className="w-8 h-8 animate-spin text-teal-400 mb-3" />
            <p className="text-sm">Connecting to Firestore & Telemetry...</p>
          </div>
        ) : (
          <>
            {activeTab === 'STAFF' && selectedCompany && (
              <StaffRoster
                company={selectedCompany}
                users={staffUsers}
                onSaveUser={handleSaveUser}
                onDeleteUser={handleDeleteUser}
              />
            )}

            {activeTab === 'COMPANIES' && isSuperAdmin && (
              <CompanyManager
                companies={companies}
                selectedCompany={selectedCompany}
                onSelectCompany={(c) => {
                  handleSelectCompany(c);
                  setActiveTab('STAFF');
                }}
                onSaveCompany={handleSaveCompany}
                onDeleteCompany={handleDeleteCompany}
              />
            )}

            {activeTab === 'LIVE_FEED' && selectedCompany && (
              <LiveScanFeed company={selectedCompany} />
            )}

            {activeTab === 'SHEETS_SYNC' && selectedCompany && (
              <div className="space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <Sheet className="w-5 h-5 text-emerald-400" />
                    Dedicated Google Spreadsheets Routing
                  </h2>
                  <p className="text-xs text-slate-400">
                    Each company is isolated to its own Google Spreadsheet document and department tabs.
                  </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                  {companies.map((comp) => (
                    <div
                      key={comp.code}
                      className="bg-[#131B24] border border-slate-800 rounded-2xl p-5 space-y-4"
                    >
                      <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                        <div>
                          <div className="text-base font-bold text-white">{comp.name}</div>
                          <div className="text-xs font-mono text-teal-400">FACILITY CODE: {comp.code}</div>
                        </div>
                        {comp.spreadsheetId && (
                          <a
                            href={`https://docs.google.com/spreadsheets/d/${comp.spreadsheetId}/edit`}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/20 transition-all"
                          >
                            Open Sheet <ExternalLink className="w-3.5 h-3.5" />
                          </a>
                        )}
                      </div>

                      <div className="space-y-2 text-xs">
                        <div className="text-slate-400">Configured Department Tabs:</div>
                        <div className="grid grid-cols-2 gap-2">
                          {Object.entries(comp.departmentTabs || {}).map(([dept, tab]) => (
                            <div key={dept} className="bg-[#0D141B] p-2.5 rounded-xl border border-slate-800">
                              <div className="text-slate-400 font-semibold">{dept}</div>
                              <div className="text-emerald-400 font-mono font-medium">{tab}</div>
                            </div>
                          ))}
                        </div>
                      </div>

                      <div className="bg-[#0D141B] p-3 rounded-xl border border-slate-800 text-[11px] font-mono text-slate-400 break-all">
                        <span className="text-slate-500">ID: </span>{comp.spreadsheetId || "No sheet linked"}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
};

export default App;
