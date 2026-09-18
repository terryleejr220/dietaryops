import React, { useState } from 'react';
import { UserPlus, QrCode, KeyRound, Shield, Trash2, Edit2, Users, Filter, CheckCircle } from 'lucide-react';
import type { Company, StaffUser, StaffRole } from '../types';
import { BadgeGeneratorModal } from './BadgeGeneratorModal';

interface StaffRosterProps {
  company: Company;
  users: StaffUser[];
  onSaveUser: (user: StaffUser) => Promise<void>;
  onDeleteUser: (employeeId: string) => Promise<void>;
}

export const StaffRoster: React.FC<StaffRosterProps> = ({
  company,
  users,
  onSaveUser,
  onDeleteUser
}) => {
  const [selectedDepartment, setSelectedDepartment] = useState<string>('ALL');
  const [selectedUserForBadge, setSelectedUserForBadge] = useState<StaffUser | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingUser, setEditingUser] = useState<StaffUser | null>(null);

  // Form state
  const [employeeId, setEmployeeId] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [department, setDepartment] = useState('Dietary');
  const [role, setRole] = useState<StaffRole>('OPERATOR');
  const [pin, setPin] = useState('1234');
  const [isSaving, setIsSaving] = useState(false);

  const openAdd = () => {
    setEditingUser(null);
    setEmployeeId('');
    setDisplayName('');
    setDepartment(company.departments[0] || 'Dietary');
    setRole('OPERATOR');
    setPin('1234');
    setShowAddModal(true);
  };

  const openEdit = (user: StaffUser) => {
    setEditingUser(user);
    setEmployeeId(user.employeeId);
    setDisplayName(user.displayName);
    setDepartment(user.department);
    setRole(user.role);
    setPin(user.pin);
    setShowAddModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!employeeId.trim() || !displayName.trim()) return;

    setIsSaving(true);
    const userData: StaffUser = {
      employeeId: employeeId.trim().toUpperCase(),
      displayName: displayName.trim(),
      companyCode: company.code,
      department,
      role,
      pin: pin.trim(),
      badgeToken: editingUser?.badgeToken || `TKN-${Math.floor(100000 + Math.random() * 900000)}`,
      active: true
    };

    await onSaveUser(userData);
    setIsSaving(false);
    setShowAddModal(false);
  };

  const filteredUsers = users.filter((u) => {
    if (selectedDepartment === 'ALL') return true;
    return u.department.toLowerCase() === selectedDepartment.toLowerCase();
  });

  return (
    <div className="space-y-6">
      {/* Top Header & Department Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Users className="w-5 h-5 text-teal-400" />
            Staff Roster & ID Badges
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-teal-500/20 text-teal-300 font-mono">
              {company.name} ({company.code})
            </span>
          </h2>
          <p className="text-xs text-slate-400">
            Manage employee IDs, 4-digit PINs, and generate scannable login badges.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Department Filter Pills */}
          <div className="flex items-center gap-1.5 bg-[#0D141B] p-1 rounded-xl border border-slate-800 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400 ml-1.5" />
            <button
              onClick={() => setSelectedDepartment('ALL')}
              className={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                selectedDepartment === 'ALL'
                  ? 'bg-teal-600 text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              All
            </button>
            {company.departments.map((dept) => (
              <button
                key={dept}
                onClick={() => setSelectedDepartment(dept)}
                className={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                  selectedDepartment === dept
                    ? 'bg-teal-600 text-white'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                {dept}
              </button>
            ))}
          </div>

          <button
            onClick={openAdd}
            className="flex items-center gap-2 px-4 py-2 text-sm font-bold text-white bg-teal-600 hover:bg-teal-500 rounded-xl transition-all shadow-lg shadow-teal-600/20 cursor-pointer"
          >
            <UserPlus className="w-4 h-4" />
            Add Staff Member
          </button>
        </div>
      </div>

      {/* Staff Table */}
      <div className="bg-[#131B24] border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-200">
            <thead className="bg-[#0D141B] text-xs uppercase text-slate-400 font-semibold border-b border-slate-800">
              <tr>
                <th className="px-6 py-3.5">Staff Member</th>
                <th className="px-6 py-3.5">Department</th>
                <th className="px-6 py-3.5">Role</th>
                <th className="px-6 py-3.5">4-Digit PIN</th>
                <th className="px-6 py-3.5">ID Badge</th>
                <th className="px-6 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-slate-500 text-sm">
                    No staff members found for this department. Click "Add Staff Member" to enroll.
                  </td>
                </tr>
              ) : (
                filteredUsers.map((user) => {
                  const initials = user.displayName
                    .split(' ')
                    .map((n) => n[0])
                    .join('')
                    .toUpperCase()
                    .slice(0, 2);

                  return (
                    <tr key={user.employeeId} className="hover:bg-slate-800/30 transition-colors">
                      {/* Name & Initials */}
                      <td className="px-6 py-4 flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-gradient-to-br from-teal-700 to-cyan-700 flex items-center justify-center font-bold text-white text-xs shadow">
                          {initials || 'ST'}
                        </div>
                        <div>
                          <div className="font-bold text-white leading-tight">
                            {user.displayName}
                          </div>
                          <div className="text-xs text-teal-400 font-mono">
                            ID: {user.employeeId}
                          </div>
                        </div>
                      </td>

                      {/* Department */}
                      <td className="px-6 py-4">
                        <span className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-slate-800 text-slate-300 border border-slate-700">
                          {user.department}
                        </span>
                      </td>

                      {/* Role */}
                      <td className="px-6 py-4">
                        <span
                          className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${
                            user.role === 'ADMIN'
                              ? 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                              : user.role === 'SUPERVISOR'
                              ? 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30'
                              : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                          }`}
                        >
                          {user.role}
                        </span>
                      </td>

                      {/* PIN */}
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 font-mono text-slate-300 bg-[#0D141B] px-2.5 py-1 rounded-lg border border-slate-800 w-fit text-xs">
                          <KeyRound className="w-3.5 h-3.5 text-slate-500" />
                          <span>{user.pin || '••••'}</span>
                        </div>
                      </td>

                      {/* ID Badge Generator Button */}
                      <td className="px-6 py-4">
                        <button
                          onClick={() => setSelectedUserForBadge(user)}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold bg-teal-500/15 text-teal-300 hover:bg-teal-500/25 border border-teal-500/30 transition-all cursor-pointer"
                        >
                          <QrCode className="w-3.5 h-3.5" />
                          Generate Badge
                        </button>
                      </td>

                      {/* Actions */}
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => openEdit(user)}
                            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
                            title="Edit Staff Member"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          {user.employeeId !== 'TL01' && (
                            <button
                              onClick={() => onDeleteUser(user.employeeId)}
                              className="p-1.5 text-rose-400/80 hover:text-rose-300 rounded-lg hover:bg-rose-500/10 transition-colors"
                              title="Delete Staff Member"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add / Edit Staff Member Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="bg-[#131B24] border border-slate-700 rounded-2xl w-full max-w-md p-6 shadow-2xl text-slate-100">
            <h3 className="text-lg font-bold text-white mb-1">
              {editingUser ? 'Edit Staff Member' : 'Enroll Staff Member'}
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Enrolls an operator for {company.name}. No email or phone is required.
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Employee ID * (e.g. TL01, EMP-102)
                </label>
                <input
                  type="text"
                  required
                  value={employeeId}
                  onChange={(e) => setEmployeeId(e.target.value.toUpperCase())}
                  placeholder="e.g. TL01"
                  disabled={!!editingUser}
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm font-mono focus:outline-none focus:border-teal-500 text-teal-300 disabled:opacity-60"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Staff Full Name *
                </label>
                <input
                  type="text"
                  required
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="e.g. Terry Little Jr."
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Department *
                </label>
                <select
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                  className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                >
                  {company.departments.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Role *
                  </label>
                  <select
                    value={role}
                    onChange={(e) => setRole(e.target.value as StaffRole)}
                    className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm focus:outline-none focus:border-teal-500 text-white"
                  >
                    <option value="OPERATOR">Operator</option>
                    <option value="SUPERVISOR">Supervisor</option>
                    <option value="ADMIN">Administrator</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    4-Digit PIN *
                  </label>
                  <input
                    type="password"
                    maxLength={6}
                    required
                    value={pin}
                    onChange={(e) => setPin(e.target.value)}
                    placeholder="1234"
                    className="w-full px-3 py-2 bg-[#0D141B] border border-slate-700 rounded-xl text-sm font-mono focus:outline-none focus:border-teal-500 text-white tracking-widest text-center"
                  />
                </div>
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
                  {isSaving ? 'Saving...' : editingUser ? 'Update Staff' : 'Enroll Staff'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Badge Generator Modal */}
      {selectedUserForBadge && (
        <BadgeGeneratorModal
          user={selectedUserForBadge}
          company={company}
          onClose={() => setSelectedUserForBadge(null)}
        />
      )}
    </div>
  );
};
