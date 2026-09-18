import React, { useState } from 'react';
import { Lock, LogIn, UserCircle } from 'lucide-react';
import type { Company, StaffUser } from '../types';
import { fetchStaffUsers } from '../services/firebase';

interface LoginScreenProps {
  companies: Company[];
  onLogin: (user: StaffUser, comp: Company) => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ companies, onLogin }) => {
  const [selectedCompCode, setSelectedCompCode] = useState<string>(companies[0]?.code || '');
  const [employeeId, setEmployeeId] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
    if (!selectedCompCode || !employeeId || !pin) {
      setError('Please fill in all fields.');
      return;
    }

    setIsLoading(true);
    try {
      const users = await fetchStaffUsers(selectedCompCode);
      const user = users.find(u => 
        u.employeeId.toUpperCase() === employeeId.trim().toUpperCase() && 
        u.pin === pin.trim()
      );

      if (!user) {
        setError('Invalid Employee ID or PIN.');
      } else if (!user.active) {
        setError('This account has been disabled.');
      } else if (user.role !== 'SUPER_ADMIN' && user.role !== 'DEPT_ADMIN' && user.role !== 'ADMIN') {
        setError('Access denied. You must be an Administrator to access the Web Dashboard.');
      } else {
        const comp = companies.find(c => c.code === selectedCompCode)!;
        onLogin(user, comp);
      }
    } catch (err: any) {
      setError(`Login failed: ${err.message || 'Network error'}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0A1016] text-slate-100 flex items-center justify-center p-4 font-sans">
      <div className="max-w-md w-full bg-[#101720] border border-slate-800 rounded-2xl shadow-2xl p-8 relative overflow-hidden">
        {/* Glow effect */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-64 h-32 bg-teal-500/20 blur-[60px] rounded-full pointer-events-none" />

        <div className="text-center mb-8 relative">
          <div className="w-16 h-16 bg-gradient-to-tr from-teal-600 to-teal-400 rounded-2xl mx-auto flex items-center justify-center shadow-lg shadow-teal-500/20 mb-4">
            <Lock className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-white">Admin Console</h1>
          <p className="text-slate-400 text-sm mt-2">Sign in to manage facilities and staff</p>
        </div>

        <form onSubmit={handleLogin} className="space-y-5 relative">
          {error && (
            <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm font-medium text-center">
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider pl-1">Facility</label>
            <select
              value={selectedCompCode}
              onChange={e => setSelectedCompCode(e.target.value)}
              className="w-full bg-[#161E28] border border-slate-700 rounded-xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-teal-500/50"
            >
              {companies.map(c => (
                <option key={c.code} value={c.code}>{c.name} ({c.code})</option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider pl-1">Employee ID</label>
            <div className="relative">
              <UserCircle className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input
                type="text"
                placeholder="e.g. TL01"
                value={employeeId}
                onChange={e => setEmployeeId(e.target.value)}
                className="w-full bg-[#161E28] border border-slate-700 rounded-xl pl-11 pr-4 py-3 text-white placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-teal-500/50"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider pl-1">4-Digit PIN</label>
            <div className="relative">
              <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input
                type="password"
                placeholder="••••"
                maxLength={4}
                value={pin}
                onChange={e => setPin(e.target.value)}
                className="w-full bg-[#161E28] border border-slate-700 rounded-xl pl-11 pr-4 py-3 text-white placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-teal-500/50 tracking-widest font-mono"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full bg-teal-600 hover:bg-teal-500 text-white font-bold py-3.5 rounded-xl transition-all shadow-lg shadow-teal-500/20 flex items-center justify-center gap-2 mt-4"
          >
            {isLoading ? (
              <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <>
                <LogIn className="w-5 h-5" />
                Sign In to Dashboard
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
