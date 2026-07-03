import { useState, useEffect } from 'react';
import api from '../api';

const GROUP_BYS = ['OVERALL', 'LOCATION', 'PRODUCT', 'DATE', 'DAY', 'MONTH'];
const METRICS = [
  { value: 'SUM_AMOUNT', label: 'Total Amount' },
  { value: 'SUM_QUANTITY', label: 'Total Quantity' },
  { value: 'SUM_TAX', label: 'Total Tax' },
  { value: 'COUNT_INVOICES', label: 'Invoice Count' },
  { value: 'COUNT_TRANSACTIONS', label: 'Transaction Count' }
];

export default function ReportBuilder() {
  const [files, setFiles] = useState([]);
  const [selectedFileId, setSelectedFileId] = useState('');
  
  const [config, setConfig] = useState({
    groupBy: 'OVERALL',
    metrics: ['SUM_AMOUNT', 'SUM_QUANTITY']
  });

  const [filters, setFilters] = useState({
    startDate: '',
    endDate: '',
    location: '',
    product: ''
  });

  const [reportData, setReportData] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.get('/files').then(res => setFiles(res.data)).catch(console.error);
  }, []);

  const getPayload = () => ({
    uploadedFileId: selectedFileId,
    groupBy: config.groupBy,
    metrics: config.metrics,
    columns: ['key', ...config.metrics],
    filters: Object.fromEntries(Object.entries(filters).filter(([_, v]) => v !== ''))
  });

  const handleRunReport = async () => {
    if (!selectedFileId) return alert('Select a file');
    if (config.metrics.length === 0) return alert('Select at least one metric');
    setLoading(true);
    try {
      const res = await api.post('/reports/generate', getPayload());
      setReportData(res.data);
    } catch (err) {
      console.error(err);
      alert('Failed to run report');
    }
    setLoading(false);
  };

  const handleExport = async (format) => {
    if (!selectedFileId) return;
    try {
      const response = await api.post(`/reports/export?format=${format}`, getPayload(), {
        responseType: 'blob'
      });
      
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `report.${format === 'excel' ? 'xlsx' : format}`);
      document.body.appendChild(link);
      link.click();
    } catch (err) {
      console.error(err);
      alert('Failed to export');
    }
  };

  return (
    <div className="space-y-6 pb-20">
      <h2 className="text-2xl font-bold">Report Builder</h2>
      
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-4">
        <div className="col-span-1 space-y-6 rounded-xl bg-[var(--color-surface)] p-6 shadow-md xl:col-span-1">
          <div>
            <label className="mb-1 block text-sm text-gray-300">Data Source (File)</label>
            <select
              className="w-full rounded border border-gray-600 bg-gray-700 p-2 text-white"
              value={selectedFileId}
              onChange={(e) => setSelectedFileId(e.target.value)}
            >
              <option value="">-- Select File --</option>
              {files.map(f => (
                <option key={f.id} value={f.id}>{f.fileName}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm text-gray-300">Report Type (Group By)</label>
            <select
              className="w-full rounded border border-gray-600 bg-gray-700 p-2 text-white"
              value={config.groupBy}
              onChange={(e) => setConfig({ ...config, groupBy: e.target.value })}
            >
              {GROUP_BYS.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm text-gray-300">Metrics (Select Multiple)</label>
            <select
              className="w-full rounded border border-gray-600 bg-gray-700 p-2 text-white h-32"
              multiple
              value={config.metrics}
              onChange={(e) => setConfig({ ...config, metrics: Array.from(e.target.selectedOptions, option => option.value) })}
            >
              {METRICS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </div>

          <div className="border-t border-gray-600 pt-4">
            <h4 className="mb-3 font-semibold text-gray-300">Filters</h4>
            <div className="space-y-3">
              <div>
                <label className="text-xs text-gray-400">Start Date</label>
                <input type="date" className="w-full rounded bg-gray-700 p-2 text-sm text-white" value={filters.startDate} onChange={e => setFilters({...filters, startDate: e.target.value})} />
              </div>
              <div>
                <label className="text-xs text-gray-400">End Date</label>
                <input type="date" className="w-full rounded bg-gray-700 p-2 text-sm text-white" value={filters.endDate} onChange={e => setFilters({...filters, endDate: e.target.value})} />
              </div>
              <div>
                <label className="text-xs text-gray-400">Location (Station)</label>
                <input type="text" placeholder="e.g. Bhopal" className="w-full rounded bg-gray-700 p-2 text-sm text-white" value={filters.location} onChange={e => setFilters({...filters, location: e.target.value})} />
              </div>
              <div>
                <label className="text-xs text-gray-400">Product</label>
                <input type="text" placeholder="e.g. Brake Pad" className="w-full rounded bg-gray-700 p-2 text-sm text-white" value={filters.product} onChange={e => setFilters({...filters, product: e.target.value})} />
              </div>
            </div>
          </div>
          
          <div className="pt-4 space-y-2">
            <button
              onClick={handleRunReport}
              disabled={loading}
              className="w-full rounded bg-[var(--color-primary)] py-2 font-semibold text-white transition hover:bg-indigo-500 disabled:opacity-50"
            >
              {loading ? 'Running...' : 'Run Report'}
            </button>
            <div className="flex gap-2">
              <button
                onClick={() => handleExport('excel')}
                disabled={!reportData}
                className="w-1/2 rounded bg-[var(--color-secondary)] py-2 font-semibold text-white transition hover:bg-emerald-400 disabled:opacity-50"
              >
                Excel
              </button>
              <button
                onClick={() => handleExport('pdf')}
                disabled={!reportData}
                className="w-1/2 rounded bg-red-600 py-2 font-semibold text-white transition hover:bg-red-500 disabled:opacity-50"
              >
                PDF
              </button>
            </div>
          </div>
        </div>

        <div className="col-span-1 rounded-xl bg-[var(--color-surface)] p-6 shadow-md overflow-x-auto xl:col-span-3">
          <h3 className="mb-4 text-lg font-semibold">Preview</h3>
          {reportData ? (
            <table className="w-full text-left text-sm text-gray-300">
              <thead className="bg-gray-800 text-xs uppercase text-gray-400">
                <tr>
                  {reportData.columns.map(k => (
                    <th key={k} className="px-4 py-3">{k}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {reportData.data.map((row, i) => (
                  <tr key={i} className="border-b border-gray-700 text-white">
                    {reportData.columns.map(k => (
                      <td key={k} className="px-4 py-3 whitespace-nowrap">
                        {typeof row[k] === 'number' ? row[k].toFixed(2) : row[k] || 'N/A'}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="flex h-32 items-center justify-center text-gray-500">Run a report to see preview</div>
          )}
        </div>
      </div>
    </div>
  );
}
