import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';
import { 
  Eye, 
  Trash2, 
  Copy, 
  Edit3, 
  Download, 
  FileSpreadsheet, 
  FileText, 
  Calendar, 
  RefreshCw,
  X,
  AlertCircle
} from 'lucide-react';

export default function SavedReports() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // Preview Modal States
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [activeReportName, setActiveReportName] = useState('');
  const [activeReportId, setActiveReportId] = useState(null);
  const [activeReportTemplate, setActiveReportTemplate] = useState(null);

  useEffect(() => {
    fetchSavedReports();
  }, []);

  const fetchSavedReports = async () => {
    setLoading(true);
    try {
      const res = await api.get('/reports/saved');
      setReports(res.data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handlePreview = async (report) => {
    setActiveReportId(report.id);
    setActiveReportName(report.name);
    setActiveReportTemplate(report.template);
    setPreviewOpen(true);
    setPreviewLoading(true);
    setPreviewData(null);
    try {
      const res = await api.get(`/reports/${report.id}/preview`);
      setPreviewData(res.data);
    } catch (err) {
      console.error(err);
      alert('Failed to load report preview.');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleDuplicate = async (id) => {
    try {
      await api.post(`/reports/${id}/duplicate`);
      alert('Report configuration duplicated!');
      fetchSavedReports();
    } catch (err) {
      console.error(err);
      alert('Failed to duplicate report.');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this report configuration?')) return;
    try {
      await api.delete(`/reports/${id}`);
      alert('Report configuration deleted.');
      fetchSavedReports();
    } catch (err) {
      console.error(err);
      alert('Failed to delete report.');
    }
  };

  const handleEdit = (report) => {
    localStorage.setItem('edit_report_config', JSON.stringify(report));
    navigate('/reports');
  };

  const handleExport = async (id, format) => {
    try {
      const res = await api.get(`/reports/${id}/export?format=${format}`, {
        responseType: 'blob'
      });
      const ext = format === 'excel' ? 'xlsx' : format;
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${activeReportName || 'report'}.${ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error(err);
      alert('Export failed.');
    }
  };

  // Helper to extract clean field names for rendering in preview card
  const getCleanFieldName = (f) => {
    if (!f) return '';
    return f.includes('.') ? f.substring(f.indexOf('.') + 1) : f;
  };

  const renderPivotMatrixTable = () => {
    if (!previewData || !previewData.data || !previewData.columns || !activeReportTemplate) return null;

    const rowDimColumns = previewData.columns.filter(c => c.accessor && c.accessor.startsWith('row_'));
    
    // Extract column prefixes
    const colPrefixes = [];
    previewData.columns.forEach(c => {
      const acc = c.accessor || '';
      const h = c.header || '';
      if (acc.startsWith('cell_')) {
        let prefix = h;
        const pivotVals = activeReportTemplate.pivotValues || [];
        if (pivotVals.length > 1) {
          const lastIdx = h.lastIndexOf(" - ");
          if (lastIdx !== -1) {
            prefix = h.substring(0, lastIdx);
          }
        }
        if (!colPrefixes.includes(prefix)) {
          colPrefixes.push(prefix);
        }
      }
    });

    const pivotVals = activeReportTemplate.pivotValues || [];
    const measures = pivotVals.map(v => getCleanFieldName(v.field));
    if (measures.length === 0) {
      measures.push("Count");
    }

    return (
      <div className="overflow-x-auto rounded-3xl border border-gray-150">
        <table className="w-full text-left bg-white text-xs border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-150">
              {/* Row dimension headers */}
              {rowDimColumns.map((col, idx) => (
                <th key={idx} className="px-5 py-4 font-bold text-gray-700 border-r border-gray-150/50">
                  {col.header}
                </th>
              ))}
              {/* Metric column header */}
              <th className="px-5 py-4 font-bold text-gray-700 border-r border-gray-150/50">
                Metric
              </th>
              {/* Column dimension headers */}
              {colPrefixes.map((prefix, idx) => (
                <th key={idx} className="px-5 py-4 font-bold text-gray-700 border-r border-gray-150/50 text-right">
                  {prefix}
                </th>
              ))}
              {/* Grand Total header */}
              <th className="px-5 py-4 font-bold text-gray-700 text-right">
                Grand Total
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {previewData.data.map((row, rowIdx) => {
              const isTotalRow = Object.keys(row).some(k => k.startsWith('row_') && (row[k] === 'Total' || row[k] === 'Grand Total'));
              
              return measures.map((measure, mIdx) => {
                const totalAccessor = `total_val_${mIdx}`;
                
                return (
                  <tr 
                    key={`${rowIdx}-${mIdx}`} 
                    className={`transition ${isTotalRow ? 'bg-indigo-50/70 font-extrabold border-y border-indigo-200/50' : 'hover:bg-gray-50/50'}`}
                  >
                    {/* Render row dimensions on the first measure row only */}
                    {mIdx === 0 && rowDimColumns.map((col, colIdx) => (
                      <td 
                        key={colIdx} 
                        rowSpan={measures.length}
                        className="px-5 py-3.5 border-r border-gray-150/30 font-bold text-gray-900 vertical-align-top align-top"
                      >
                        {row[col.accessor]}
                      </td>
                    ))}
                    
                    {/* Metric label cell */}
                    <td className="px-5 py-2.5 border-r border-gray-150/30 font-semibold text-gray-500">
                      {measure}
                    </td>
                    
                    {/* Render column prefix values */}
                    {colPrefixes.map((prefix, cpIdx) => {
                      const col = previewData.columns.find(c => {
                        const h = c.header || '';
                        if (pivotVals.length === 1) {
                          return h === prefix;
                        } else {
                          return h.startsWith(prefix + " - ") && h.endsWith(" - " + measure);
                        }
                      });
                      const val = col ? row[col.accessor] : null;
                      const displayVal = val === null || val === undefined ? '' : 
                        (typeof val === 'number' ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : val.toString());
                      
                      return (
                        <td key={cpIdx} className="px-5 py-2.5 border-r border-gray-150/30 text-right font-medium text-gray-800">
                          {displayVal}
                        </td>
                      );
                    })}
                    
                    {/* Grand Total cell */}
                    <td className="px-5 py-2.5 text-right font-extrabold text-indigo-950">
                      {row[totalAccessor] !== undefined && row[totalAccessor] !== null ? 
                        row[totalAccessor].toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : ''}
                    </td>
                  </tr>
                );
              });
            })}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      <header className="flex justify-between items-center">
        <div>
          <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight flex items-center gap-2">
            Saved Report Templates
          </h1>
          <p className="text-gray-500 mt-2 font-medium text-lg">
            Access and generate standard pivot reports immediately.
          </p>
        </div>
        
        <button
          onClick={fetchSavedReports}
          className="p-3 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-xl transition flex items-center gap-2 text-sm shadow-sm"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh List
        </button>
      </header>

      {loading ? (
        <div className="flex justify-center p-24">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-indigo-500 border-t-transparent"></div>
        </div>
      ) : reports.length === 0 ? (
        <div className="bg-white/80 backdrop-blur-xl border border-gray-100 p-16 rounded-[2.5rem] shadow-sm text-center">
          <div className="text-5xl mb-4">📊</div>
          <h3 className="text-2xl font-bold text-gray-800">No saved templates found</h3>
          <p className="text-gray-500 mt-2 font-medium">
            Go to the designer page to create and configure a new pivot table report.
          </p>
          <button
            onClick={() => navigate('/reports')}
            className="mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl transition shadow-lg shadow-indigo-100"
          >
            Create Report Config
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 animate-in fade-in duration-300">
          {reports.map(report => (
            <div 
              key={report.id} 
              className="bg-white border border-gray-150 p-6 rounded-[2rem] shadow-sm hover:shadow-xl hover:shadow-indigo-50/50 transition-all flex flex-col justify-between relative overflow-hidden group"
            >
              <div>
                <div className="flex justify-between items-start mb-4">
                  {report.snapshotMode ? (
                    <span className="px-3 py-1 bg-indigo-50 text-indigo-700 text-[10px] font-bold rounded-full border border-indigo-100">
                      🔒 Snapshot Mode (Static Copy)
                    </span>
                  ) : (
                    <span className="px-3 py-1 bg-emerald-50 text-emerald-700 text-[10px] font-bold rounded-full border border-emerald-100">
                      ⚡ Dynamic Live Report
                    </span>
                  )}
                  
                  <span className="text-[10px] text-gray-400 font-bold flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    ID: {report.id}
                  </span>
                </div>

                <h3 className="text-xl font-bold text-gray-800 mb-4 truncate pr-4">{report.name}</h3>
                
                {/* Mini Preview Visualization */}
                <div className="border border-gray-150 rounded-2xl p-4 bg-gray-50/50 mb-5 text-[10px] space-y-2">
                  <div className="flex justify-between border-b border-gray-150/40 pb-1">
                    <span className="text-gray-400 font-bold">Rows</span>
                    <span className="font-extrabold text-gray-700">
                      {report.template?.pivotRows?.length ? report.template.pivotRows.map(r => getCleanFieldName(r)).join(', ') : '-'}
                    </span>
                  </div>
                  <div className="flex justify-between border-b border-gray-150/40 pb-1">
                    <span className="text-gray-400 font-bold">Columns</span>
                    <span className="font-extrabold text-gray-700">
                      {report.template?.pivotColumns?.length ? report.template.pivotColumns.map(c => getCleanFieldName(c)).join(', ') : '-'}
                    </span>
                  </div>
                  <div className="flex justify-between pb-0.5">
                    <span className="text-gray-400 font-bold">Measures</span>
                    <span className="font-extrabold text-emerald-600">
                      {report.template?.pivotValues?.length ? report.template.pivotValues.map(v => `${v.aggregation}(${getCleanFieldName(v.field)})`).join(', ') : '-'}
                    </span>
                  </div>
                </div>

                {/* Timestamps */}
                <div className="text-[9px] text-gray-400 space-y-0.5 mb-5 font-semibold">
                  <p>Created: {new Date(report.createdAt).toLocaleString()}</p>
                </div>
              </div>

              {/* Actions Grid */}
              <div className="border-t border-gray-100 pt-5 space-y-2">
                <div className="flex gap-1.5 w-full">
                  <button 
                    onClick={() => handleExport(report.id, 'excel')} 
                    className="flex-1 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-[10px] font-bold rounded-lg border border-emerald-100 transition flex items-center justify-center gap-0.5"
                    title="Download Excel"
                  >
                    <Download className="w-3 h-3" />
                    Excel
                  </button>
                  <button 
                    onClick={() => handleExport(report.id, 'pdf')} 
                    className="flex-1 py-1.5 bg-red-50 hover:bg-red-100 text-red-700 text-[10px] font-bold rounded-lg border border-red-100 transition flex items-center justify-center gap-0.5"
                    title="Download PDF"
                  >
                    <Download className="w-3 h-3" />
                    PDF
                  </button>
                </div>

                <div className="flex gap-2 pt-1.5">
                  <button 
                    onClick={() => handlePreview(report)} 
                    className="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1 shadow-md shadow-indigo-100"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    Live Preview
                  </button>
                  <button 
                    onClick={() => handleEdit(report)}
                    className="px-3.5 py-2.5 border hover:bg-gray-50 text-gray-600 rounded-xl transition flex items-center justify-center"
                    title="Edit Configuration"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button 
                    onClick={() => handleDuplicate(report.id)}
                    className="px-3.5 py-2.5 border hover:bg-gray-50 text-gray-600 rounded-xl transition flex items-center justify-center"
                    title="Duplicate Report"
                  >
                    <Copy className="w-3.5 h-3.5" />
                  </button>
                  <button 
                    onClick={() => handleDelete(report.id)}
                    className="px-3.5 py-2.5 border border-red-100 hover:bg-red-50 text-red-500 rounded-xl transition flex items-center justify-center"
                    title="Delete Report"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Preview Modal */}
      {previewOpen && (
        <div className="fixed inset-0 bg-gray-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-[2rem] p-8 max-w-5xl w-full max-h-[85vh] shadow-2xl border border-gray-100 flex flex-col animate-in zoom-in-95 duration-200">
            
            {/* Modal Header */}
            <div className="flex justify-between items-center border-b border-gray-100 pb-4 mb-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
                  <Eye className="text-indigo-600 w-6 h-6" />
                  {activeReportName}
                </h2>
                <span className="text-xs text-gray-400 mt-1 block">
                  Report generated using latest monthly datasets dynamically.
                </span>
              </div>
              
              <button 
                onClick={() => setPreviewOpen(false)} 
                className="p-1.5 hover:bg-gray-100 rounded-xl transition text-gray-400 hover:text-gray-600"
              >
                <X className="w-6 h-6" />
              </button>
            </div>

            {/* Modal Content */}
            <div className="flex-1 overflow-y-auto pr-1">
              {previewLoading ? (
                <div className="flex flex-col justify-center items-center py-20 space-y-3">
                  <div className="animate-spin rounded-full h-10 w-10 border-4 border-indigo-500 border-t-transparent"></div>
                  <span className="text-gray-500 text-xs font-semibold">Running report calculations...</span>
                </div>
              ) : previewData ? (
                <div className="space-y-6">
                  {/* Downloads toolbar */}
                  <div className="flex justify-end gap-2 bg-gray-50 p-2.5 rounded-xl border">
                    <button 
                      onClick={() => handleExport(activeReportId, 'excel')} 
                      className="px-3.5 py-2 bg-white hover:bg-emerald-50 text-emerald-700 font-bold border border-emerald-200 rounded-lg text-xs flex items-center gap-1.5 transition"
                    >
                      <FileSpreadsheet className="w-4 h-4" />
                      Download Excel
                    </button>
                    <button 
                      onClick={() => handleExport(activeReportId, 'pdf')} 
                      className="px-3.5 py-2 bg-white hover:bg-rose-50 text-rose-700 font-bold border border-rose-200 rounded-lg text-xs flex items-center gap-1.5 transition"
                    >
                      <FileText className="w-4 h-4" />
                      Download PDF
                    </button>
                  </div>

                  {activeReportTemplate?.reportType === 'PIVOT' && activeReportTemplate?.pivotColumns?.length > 0 ? (
                    renderPivotMatrixTable()
                  ) : (
                    // Flat layout table
                    <div className="overflow-x-auto border border-gray-100 rounded-xl">
                      <table className="w-full text-left bg-white border-collapse">
                        <thead className="bg-gray-50 text-gray-600 font-bold uppercase text-xs border-b border-gray-100">
                          <tr>
                            {previewData.columns && previewData.columns.map((col, idx) => (
                              <th key={idx} className="px-5 py-3 border-r border-gray-100 last:border-r-0">
                                {col.header}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 text-gray-800 text-sm font-semibold">
                          {previewData.data && previewData.data.map((row, rIdx) => {
                            const isGrandTotal = Object.keys(row).some(k => k.startsWith('row_') && (row[k] === 'Total' || row[k] === 'Grand Total'));
                            return (
                              <tr 
                                key={rIdx} 
                                className={`transition-colors ${isGrandTotal ? 'bg-indigo-50/70 font-extrabold border-y-2 border-indigo-200/50' : 'hover:bg-indigo-50/10'}`}
                              >
                                {previewData.columns.map((col, cIdx) => {
                                  const val = row[col.accessor];
                                  return (
                                    <td key={cIdx} className="px-5 py-3 border-r border-gray-100 last:border-r-0">
                                      {val !== null && val !== undefined
                                        ? (typeof val === 'number' 
                                            ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) 
                                            : val.toString())
                                        : '-'}
                                    </td>
                                  );
                                })}
                              </tr>
                            );
                          })}
                          {(!previewData.data || previewData.data.length === 0) && (
                            <tr>
                              <td colSpan={previewData.columns ? previewData.columns.length : 1} className="px-5 py-8 text-center text-gray-400 font-normal">
                                <AlertCircle className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                                No rows populated.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center text-gray-400 py-10">No preview data found.</div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="border-t border-gray-100 pt-4 mt-6 flex justify-end">
              <button 
                onClick={() => setPreviewOpen(false)}
                className="px-6 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-xl transition text-sm"
              >
                Close Preview
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
