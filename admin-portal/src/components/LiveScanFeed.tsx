import React, { useEffect, useState } from 'react';
import { Radio, CheckCircle2, Calendar, Printer, Sheet, Package } from 'lucide-react';
import type { Company, ScanRecord } from '../types';
import { subscribeLiveScans } from '../services/firebase';

interface LiveScanFeedProps {
  company: Company;
}

export const LiveScanFeed: React.FC<LiveScanFeedProps> = ({ company }) => {
  const [scans, setScans] = useState<ScanRecord[]>([]);

  useEffect(() => {
    const unsubscribe = subscribeLiveScans(company.code, (records) => {
      setScans(records);
    });

    return () => unsubscribe();
  }, [company.code]);

  // Demo Fallback Data if Firestore table has no records yet
  const displayScans = scans.length > 0 ? scans : [
    {
      id: "scan-001",
      syscoUpc: "10074471012345",
      itemName: "Sysco Classic Whole Milk Grade A",
      deliveryDate: new Date().toISOString().split('T')[0],
      useByDate: new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0],
      category: "Dairy & Fresh",
      shelfLifeDays: 7,
      unit: "CS",
      onHandAmount: 3.0,
      scanTimestamp: Date.now() - 1000 * 60 * 5,
      printed: true,
      syncedToSheets: true,
      isAudit: false,
      receivedBy: "Terry L. (TL01)"
    },
    {
      id: "scan-002",
      syscoUpc: "00021000010998",
      itemName: "Kraft Real Mayo Squeeze",
      deliveryDate: new Date().toISOString().split('T')[0],
      useByDate: new Date(Date.now() + 180 * 86400000).toISOString().split('T')[0],
      category: "Condiments & Sauces",
      shelfLifeDays: 180,
      unit: "CS",
      onHandAmount: 2.0,
      scanTimestamp: Date.now() - 1000 * 60 * 35,
      printed: true,
      syncedToSheets: true,
      isAudit: false,
      receivedBy: "Terry L. (TL01)"
    }
  ];

  const totalScans = displayScans.length;
  const printedCount = displayScans.filter((s) => s.printed).length;
  const syncedCount = displayScans.filter((s) => s.syncedToSheets).length;

  return (
    <div className="space-y-6">
      {/* Header & Status Indicator */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Radio className="w-5 h-5 text-emerald-400 animate-pulse" />
            Live Kitchen Delivery Feed
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-mono">
              {company.name} ({company.code})
            </span>
          </h2>
          <p className="text-xs text-slate-400">
            Real-time telemetry of incoming barcodes, shelf-life calculations, and Zebra label prints.
          </p>
        </div>

        <div className="flex items-center gap-2 bg-emerald-500/10 border border-emerald-500/30 px-3 py-1.5 rounded-xl text-xs text-emerald-400 font-mono">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
          LISTENING FOR SCANS
        </div>
      </div>

      {/* KPI Header Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-[#131B24] border border-slate-800 rounded-2xl p-4 flex items-center gap-3">
          <div className="p-3 bg-teal-500/10 rounded-xl border border-teal-500/20 text-teal-400">
            <Package className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400">Total Deliveries Logged</div>
            <div className="text-2xl font-extrabold text-white">{totalScans}</div>
          </div>
        </div>

        <div className="bg-[#131B24] border border-slate-800 rounded-2xl p-4 flex items-center gap-3">
          <div className="p-3 bg-cyan-500/10 rounded-xl border border-cyan-500/20 text-cyan-400">
            <Printer className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400">Labels Printed</div>
            <div className="text-2xl font-extrabold text-white">{printedCount}</div>
          </div>
        </div>

        <div className="bg-[#131B24] border border-slate-800 rounded-2xl p-4 flex items-center gap-3">
          <div className="p-3 bg-emerald-500/10 rounded-xl border border-emerald-500/20 text-emerald-400">
            <Sheet className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs text-slate-400">Synced to Google Sheet</div>
            <div className="text-2xl font-extrabold text-emerald-400">{syncedCount}</div>
          </div>
        </div>
      </div>

      {/* Scan Log Items List */}
      <div className="space-y-3">
        {displayScans.map((record) => {
          const scanTime = new Date(record.scanTimestamp).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
          });

          return (
            <div
              key={record.id}
              className="bg-[#131B24] border border-slate-800 hover:border-slate-700/80 rounded-2xl p-4 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4"
            >
              <div className="flex items-start gap-3.5">
                <div className="p-2.5 bg-slate-800 rounded-xl text-teal-400 border border-slate-700/80 shrink-0 mt-0.5">
                  <Package className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-base font-bold text-white leading-snug">
                      {record.itemName}
                    </span>
                    <span className="text-[11px] px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 font-semibold border border-slate-700">
                      {record.category}
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400 mt-1 font-mono">
                    <span className="text-teal-300">UPC: {record.syscoUpc}</span>
                    <span>Qty: {record.onHandAmount} {record.unit}</span>
                    {record.receivedBy && <span>Staff: {record.receivedBy}</span>}
                  </div>
                </div>
              </div>

              {/* Expiration & Sync Telemetry */}
              <div className="flex flex-wrap items-center gap-3 shrink-0">
                {/* Dates */}
                <div className="bg-[#0D141B] px-3 py-1.5 rounded-xl border border-slate-800 text-xs">
                  <div className="text-[10px] text-slate-500 font-medium">USE-BY DATE</div>
                  <div className="font-bold text-emerald-400 font-mono flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    {record.useByDate}
                  </div>
                </div>

                {/* Print Status */}
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-slate-800/80 text-xs font-semibold text-slate-300 border border-slate-700">
                  <Printer className="w-3.5 h-3.5 text-cyan-400" />
                  <span>{record.printed ? 'Printed' : 'Pending'}</span>
                </div>

                {/* Sheets Status */}
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-emerald-500/10 text-xs font-bold text-emerald-400 border border-emerald-500/30">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Synced</span>
                </div>

                <div className="text-[11px] text-slate-500 font-mono pl-1">
                  {scanTime}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
