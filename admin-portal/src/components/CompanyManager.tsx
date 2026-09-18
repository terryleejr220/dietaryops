import React, { useState } from 'react';
import { Building2, Plus, ExternalLink, Trash2, Edit3, CheckCircle2, Sheet, Layers } from 'lucide-react';
import type { Company } from '../types';

interface CompanyManagerProps {
  companies: Company[];
  selectedCompany: Company | null;
  onSelectCompany: (company: Company) => void;
  onSaveCompany: (company: Company) => Promise<void>;
  onDeleteCompany: (code: string) => Promise<void>;
}

export const CompanyManager: React.FC<CompanyManagerProps> = ({
  companies,
  selectedCompany,
  onSelectCompany,
  onSaveCompany,
  onDeleteCompany
}) => {
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);

  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [spreadsheetId, setSpreadsheetId] = useState('');
  const [webAppUrl, setWebAppUrl] = useState('');
  const [departmentsStr, setDepartmentsStr] = useState('Dietary, Housekeeping, Nursing, General');
  const [sheetTab, setSheetTab] = useState('Delivery Log');
  const [isSaving, setIsSaving] = useState(false);

  const openAdd = () => {
    setEditingCompany(null);
    setName('');
    setCode('');
    setSpreadsheetId('');
    setWebAppUrl('');
    setDepartmentsStr('Dietary, Housekeeping, Nursing, General');
    setSheetTab('Delivery Log');
    setShowAddModal(true);
  };

  const openEdit = (comp: Company) => {
    setEditingCompany(comp);
    setName(comp.name);
    setCode(comp.code);
    setSpreadsheetId(comp.spreadsheetId);
    setWebAppUrl(comp.webAppUrl);
    setDepartmentsStr(comp.departments.join(', '));
    setSheetTab(comp.departmentTabs['Dietary'] || 'Delivery Log');
    setShowAddModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !code.trim()) return;

    setIsSaving(true);
    const depts = departmentsStr.split(',').map(d => d.trim()).filter(Boolean);
    const tabs: Record<string, string> = {};
    depts.forEach(d => {
      tabs[d] = `${d} Scans`;
    });
    tabs['Dietary'] = sheetTab.trim() || 'Delivery Log';

    const compData: Company = {
      id: code.trim().toUpperCase(),
      name: name.trim(),
      code: code.trim().toUpperCase(),
      spreadsheetId: spreadsheetId.trim(),
      webAppUrl: webAppUrl.trim(),
      departments: depts.length ? depts : ['Dietary'],
      departmentTabs: tabs,
      active: true
    };

    await onSaveCompany(compData);
    setIsSaving(false);
    setShowAddModal(false);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Building2 className="w-5 h-5 text-teal-400" />
            Stores & Healthcare Facilities
          </h2>
          <p className="text-xs text-slate-400">
            Configure isolated companies, facility codes, and dedicated Google Spreadsheets.
          </p>
        </div>
        <button
          onClick={openAdd}
          className="flex items-center gap-2 px-4 py-2 text-sm font-bold text-white bg-teal-600 hover:bg-teal-500 rounded-xl transition-all shadow-lg shadow-teal-600/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          Add Store / Facility
        </button>
      </div>

      {/* Grid of Companies */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {companies.map((comp) => {
          const isSelected = selectedCompany?.code === comp.code;
          return (
            <div
              key={comp.code}
              className={`bg-[#131B24] border rounded-2xl p-5 transition-all flex flex-col justify-between relative overflow-hidden ${
                isSelected
                  ? 'border-teal-500 shadow-xl shadow-teal-500/10'
                  : 'border-slate-800 hover:border-slate-700'
              }`}
            >
              {isSelected && (
                <div className="absolute top-0 right-0 bg-teal-500 text-white text-[10px] font-bold px-3 py-1 rounded-bl-xl flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3" /> ACTIVE CONTEXT
                </div>
              )}

              <div>
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <span className="text-xs font-mono font-bold px-2 py-0.5 rounded-md bg-teal-500/15 text-teal-300 border border-teal-500/30">
                      {comp.code}
                    </span>
                    <h3 className="text-lg font-bold text-white mt-1.5 leading-snug">
                      {comp.name}
                    </h3>
                  </div>
                </div>

                {/* Spreadsheet Info */}
                <div className="bg-[#0D141B] rounded-xl p-3 border border-slate-800/80 mb-4 text-xs space-y-2">
                  <div className="flex items-center justify-between text-slate-300">
                    <span className="flex items-center gap-1.5 text-slate-400">
                      <Sheet className="w-3.5 h-3.5 text-emerald-400" />
                      Google Spreadsheet:
                    </span>
                    {comp.spreadsheetId ? (
                      <a
                        href={`https://docs.google.com/spreadsheets/d/${comp.spreadsheetId}/edit`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-emerald-400 hover:text-emerald-300 flex items-center gap-1 font-mono text-[11px]"
                      >
                        Open Sheet <ExternalLink className="w-3 h-3" />
                      </a>
                    ) : (
                      <span className="text-slate-500">Not configured</span>
                    )}
                  </div>

                  <div className="flex items-center justify-between text-slate-300">
                    <span className="flex items-center gap-1.5 text-slate-400">
                      <Layers className="w-3.5 h-3.5 text-teal-400" />
                      Departments:
                    </span>
                    <span className="font-semibold text-slate-200">
                      {comp.departments?.length || 0} configured
                    </span>
                  </div>
                </div>
              </div>

              {/* Actions Footer */}
              <div className="flex items-center justify-between pt-3 border-t border-slate-800/80">
                <button
                  onClick={() => onSelectCompany(comp)}
                  className={`text-xs px-3 py-1.5 rounded-lg font-semibold transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-teal-500/20 text-teal-300'
                      : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
                  }`}
                >
                  {isSelected ? 'Selected' : 'Manage Staff'}
                </button>

                <div className="flex items-center gap-1">
                  <button
                    onClick={() => openEdit(comp)}
                    className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
                    title="Edit Facility"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  {comp.code !== 'DOPS' && (
                    <button
                      onClick={() => onDeleteCompany(comp.code)}
                      className="p-1.5 text-rose-400/80 hover:text-rose-300 rounded-lg hover:bg-rose-500/10 transition-colors"
                      title="Delete Facility"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Add / Edit Company Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="bg-[#131B24] border border-slate-700 rounded-2xl w-full max-w-lg p-6 shadow-2xl text-slate-100">
            <h3 className="text-lg font-bold text-white mb-1">
              {editingCompany ? 'Edit Store / Facility' : 'Add Store / Facility'}
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Set up dedicated Google Spreadsheet and department structure.
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Facility Display Name *
                </label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Main Kitchen / Healthcare Center"
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Unique Facility Code * (Uppercase, used on tablets)
                </label>
                <input
                  type="text"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="e.g. DOPS"
                  disabled={!!editingCompany}
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm font-mono focus:outline-none focus:border-teal-500 text-teal-300 disabled:opacity-60"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Dedicated Google Spreadsheet ID
                </label>
                <input
                  type="text"
                  value={spreadsheetId}
                  onChange={(e) => setSpreadsheetId(e.target.value)}
                  placeholder="e.g. 16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY"
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm font-mono focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Google Apps Script Web App URL
                </label>
                <input
                  type="text"
                  value={webAppUrl}
                  onChange={(e) => setWebAppUrl(e.target.value)}
                  placeholder="https://script.google.com/macros/s/.../exec"
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm font-mono focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Departments (comma-separated)
                </label>
                <input
                  type="text"
                  value={departmentsStr}
                  onChange={(e) => setDepartmentsStr(e.target.value)}
                  placeholder="Dietary, Housekeeping, Nursing, General"
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Dietary Sheet Tab Name
                </label>
                <input
                  type="text"
                  value={sheetTab}
                  onChange={(e) => setSheetTab(e.target.value)}
                  placeholder="Delivery Log"
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 text-sm text-slate-400 hover:text-white bg-slate-800 rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2 text-sm font-bold text-white bg-teal-600 hover:bg-teal-500 rounded-xl disabled:opacity-50"
                >
                  {isSaving ? 'Saving...' : editingCompany ? 'Update Facility' : 'Create Facility'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
