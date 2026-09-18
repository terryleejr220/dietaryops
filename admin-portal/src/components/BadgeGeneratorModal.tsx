import React, { useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Printer, X, Shield, Sparkles, Building2 } from 'lucide-react';
import type { Company, StaffUser } from '../types';

interface BadgeGeneratorModalProps {
  user: StaffUser;
  company: Company;
  onClose: () => void;
}

export const BadgeGeneratorModal: React.FC<BadgeGeneratorModalProps> = ({ user, company, onClose }) => {
  const printRef = useRef<HTMLDivElement>(null);

  const badgePayload = `DOPS-AUTH:${company.code.trim().toUpperCase()}:${user.employeeId.trim().toUpperCase()}:${user.badgeToken || 'TOKEN'}`;

  const handlePrint = () => {
    window.print();
  };

  const getRoleBadgeColor = () => {
    switch (user.role) {
      case 'ADMIN':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/40';
      case 'SUPERVISOR':
        return 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40';
      default:
        return 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-[#131B24] border border-slate-700/80 rounded-2xl w-full max-w-xl p-6 shadow-2xl relative text-slate-100">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-teal-500/10 rounded-xl border border-teal-500/30 text-teal-400">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white flex items-center gap-2">
                Employee ID Badge
                <span className="text-xs px-2 py-0.5 rounded-full bg-teal-500/20 text-teal-300 font-mono">
                  CR80 Ready
                </span>
              </h2>
              <p className="text-xs text-slate-400">
                Scannable badge with encoded credentials for instant tablet login.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Printable Badge Preview Area */}
        <div className="py-6 flex flex-col items-center justify-center">
          <div
            id="printable-badge"
            ref={printRef}
            className="w-[380px] h-[240px] bg-gradient-to-br from-[#0F222D] via-[#102B37] to-[#0A1821] rounded-2xl border-2 border-teal-500/40 shadow-2xl p-4 flex flex-col justify-between relative overflow-hidden text-slate-100 print:border-black print:text-black print:bg-white"
          >
            {/* Top Gloss Background Accent */}
            <div className="absolute -top-10 -right-10 w-40 h-40 bg-teal-500/15 rounded-full blur-2xl pointer-events-none" />

            {/* Badge Top Header */}
            <div className="flex items-start justify-between border-b border-teal-500/30 pb-2 z-10">
              <div>
                <div className="flex items-center gap-1.5 text-teal-300 font-semibold text-xs tracking-wider uppercase">
                  <Building2 className="w-3.5 h-3.5" />
                  {company.name || "DietaryOps Facility"}
                </div>
                <div className="text-[10px] text-slate-400 font-mono tracking-wide">
                  FACILITY CODE: {company.code}
                </div>
              </div>
              <div className="text-right">
                <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${getRoleBadgeColor()}`}>
                  {user.role}
                </span>
              </div>
            </div>

            {/* Badge Body */}
            <div className="flex items-center justify-between gap-4 my-auto z-10">
              {/* Staff Info */}
              <div className="flex flex-col gap-1 flex-1">
                <div className="text-[11px] text-teal-400 font-medium tracking-wide">
                  {user.department || "Dietary"}
                </div>
                <div className="text-base font-extrabold text-white leading-tight">
                  {user.displayName || "Staff Member"}
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-300 font-mono pt-1">
                  <span className="text-slate-500">ID:</span>
                  <span className="font-bold text-teal-300 bg-teal-950/60 px-1.5 py-0.5 rounded border border-teal-800/40">
                    {user.employeeId}
                  </span>
                </div>
              </div>

              {/* Scannable High-Density QR Code */}
              <div className="bg-white p-2 rounded-xl shadow-lg border border-teal-500/30 flex flex-col items-center">
                <QRCodeSVG
                  value={badgePayload}
                  size={88}
                  level="M"
                  includeMargin={false}
                />
                <span className="text-[8px] text-slate-800 font-mono font-bold mt-1 tracking-tighter">
                  SCAN TO UNLOCK
                </span>
              </div>
            </div>

            {/* Badge Footer */}
            <div className="flex items-center justify-between border-t border-teal-500/20 pt-1.5 text-[9px] text-slate-400 z-10">
              <div className="flex items-center gap-1">
                <Sparkles className="w-3 h-3 text-teal-400" />
                <span>DietaryOps Security Verified</span>
              </div>
              <div className="font-mono text-[8px] text-slate-500">
                TOKEN: {user.badgeToken?.slice(0, 10)}
              </div>
            </div>
          </div>
        </div>

        {/* Action Controls */}
        <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
          <div className="text-xs text-slate-400">
            Print size formatted for standard <span className="text-teal-400 font-semibold">CR80 (3.375" x 2.125")</span> card printers or label sheets.
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handlePrint}
              className="flex items-center gap-2 px-5 py-2 text-sm font-bold text-white bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 rounded-xl shadow-lg shadow-teal-500/20 transition-all cursor-pointer"
            >
              <Printer className="w-4 h-4" />
              Print Badge
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
