import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { 
  Plus, 
  Trash2, 
  Upload, 
  Database, 
  CheckCircle, 
  AlertCircle, 
  FileText, 
  Calendar, 
  List, 
  Eye,
  Layers,
  Sparkles
} from 'lucide-react';

const Files = () => {
  const [sources, setSources] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [statusMsg, setStatusMsg] = useState({ text: '', type: '' });
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newSource, setNewSource] = useState({ name: '', internalKey: '', tableNumber: '' });
  const [uploadingSourceId, setUploadingSourceId] = useState(null);

  const fetchSources = async () => {
    try {
      const res = await api.get('/files/sources');
      setSources(res.data || []);
    } catch (err) {
      setError('Failed to fetch report sources.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSources();
  }, []);

  // Poll if any file is processing/normalizing
  useEffect(() => {
    let interval;
    const hasPendingOrProcessing = sources.some(s => 
      s.files && s.files.some(f => 
        f.normalizationStatus === 'PROCESSING' || 
        f.normalizationStatus === 'PENDING' || 
        f.normalizationStatus === 'NORMALIZING'
      )
    );

    if (hasPendingOrProcessing) {
      interval = setInterval(() => {
        fetchSources();
      }, 3000);
    }
    return () => clearInterval(interval);
  }, [sources]);

  const showStatus = (text, type = 'success') => {
    setStatusMsg({ text, type });
    setTimeout(() => setStatusMsg({ text: '', type: '' }), 5000);
  };

  const handleCreateSource = async (e) => {
    e.preventDefault();
    if (!newSource.name.trim() || !newSource.internalKey.trim()) {
      alert('Please fill out Name and Internal Key.');
      return;
    }
    try {
      const payload = {
        name: newSource.name.trim(),
        internalKey: newSource.internalKey.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_'),
        tableNumber: newSource.tableNumber ? parseInt(newSource.tableNumber) : null
      };

      await api.post('/files/sources', payload);

      showStatus('Report Source created successfully!');
      setIsModalOpen(false);
      setNewSource({ name: '', internalKey: '', tableNumber: '' });
      fetchSources();
    } catch (err) {
      alert('Error creating Report Source: ' + (err.response?.data || err.message));
    }
  };

  const handleDeleteSource = async (sourceId) => {
    if (!window.confirm('Are you sure you want to delete this Report Source and ALL its files? This will instantly remove its tables from your reports.')) return;
    try {
      await api.delete(`/files/sources/${sourceId}`);
      showStatus('Report Source deleted successfully.', 'error');
      fetchSources();
    } catch (err) {
      alert('Error deleting Report Source.');
    }
  };

  const handleUploadFile = async (e, sourceId) => {
    const file = e.target.files[0];
    if (!file) return;

    setUploadingSourceId(sourceId);
    try {
      const formData = new FormData();
      formData.append('file', file);

      await api.post(`/files/sources/${sourceId}/upload`, formData, {
        headers: { 
          'Content-Type': 'multipart/form-data'
        }
      });
      showStatus('File uploaded and queued for processing successfully.');
      fetchSources();
    } catch (err) {
      alert('Error uploading file: ' + (err.response?.data || err.message));
    } finally {
      setUploadingSourceId(null);
    }
  };

  const handleDeleteFile = async (fileId) => {
    if (!window.confirm('Delete this file and remove its rows? Reports using this source will automatically update.')) return;
    try {
      await api.delete(`/files/${fileId}`);
      showStatus('File deleted successfully.', 'error');
      fetchSources();
    } catch (err) {
      alert('Error deleting file.');
    }
  };

  const handleReplaceFile = async (e, fileId) => {
    const file = e.target.files[0];
    if (!file) return;

    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);

      await api.post(`/files/${fileId}/replace`, formData, {
        headers: { 
          'Content-Type': 'multipart/form-data'
        }
      });
      showStatus('File replaced successfully and queued for processing.');
      fetchSources();
    } catch (err) {
      alert('Error replacing file: ' + (err.response?.data || err.message));
    } finally {
      setLoading(false);
    }
  };

  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = 2;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  };

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-24">
      {/* Header */}
      <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight flex items-center gap-3">
            <Layers className="text-indigo-600 w-9 h-9" />
            Report Sources Manager
          </h1>
          <p className="text-gray-500 mt-2 font-medium text-lg">
            Configure permanent source tables and upload monthly data spreadsheets.
          </p>
        </div>
        <button
          onClick={() => setIsModalOpen(true)}
          className="px-5 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-2xl transition shadow-lg shadow-indigo-150 flex items-center gap-2 text-sm"
        >
          <Plus className="w-5 h-5" />
          Add Report Source
        </button>
      </header>

      {statusMsg.text && (
        <div className={`p-4 rounded-2xl font-bold text-center animate-in fade-in ${
          statusMsg.type === 'error' ? 'bg-red-50 text-red-600 border border-red-100' : 'bg-green-50 text-green-700 border border-green-100'
        }`}>
          {statusMsg.text}
        </div>
      )}

      {error ? (
        <div className="text-red-500 font-bold text-center py-10 bg-red-50 rounded-2xl border">{error}</div>
      ) : loading ? (
        <div className="flex justify-center p-20">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-indigo-500 border-t-transparent"></div>
        </div>
      ) : (
        <div className="space-y-8">
          {sources.length === 0 ? (
            <div className="bg-white/80 border border-gray-100 p-16 rounded-[2.5rem] shadow-sm text-center">
              <div className="text-5xl mb-4">📁</div>
              <h3 className="text-2xl font-bold text-gray-800">No Report Sources Configured</h3>
              <p className="text-gray-500 mt-2 font-medium">
                Create a permanent report source (e.g. Bhopal Offtake, Purchase Report) to start importing files.
              </p>
              <button
                onClick={() => setIsModalOpen(true)}
                className="mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl transition shadow-lg shadow-indigo-100"
              >
                Create Source
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-8">
              {sources.map((source) => (
                <div 
                  key={source.id} 
                  className="bg-white border border-gray-150 rounded-[2rem] p-8 shadow-sm flex flex-col justify-between hover:shadow-md transition relative overflow-hidden group"
                >
                  <div className="absolute -right-16 -top-16 w-32 h-32 bg-indigo-50 rounded-full -z-10 group-hover:scale-110 transition-transform"></div>
                  
                  {/* Source Header */}
                  <div className="flex flex-col md:flex-row justify-between items-start md:items-center border-b border-gray-100 pb-5 mb-6 gap-4 w-full">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <span className="bg-indigo-50 text-indigo-700 font-extrabold text-[10px] uppercase tracking-wider px-3 py-1 rounded-full border border-indigo-100 flex-shrink-0">
                          Table {source.tableNumber}
                        </span>
                        <span className="text-[10px] font-mono text-gray-400 font-bold uppercase tracking-wider truncate max-w-xs md:max-w-md block" title={source.internalKey}>
                          Key: {source.internalKey}
                        </span>
                      </div>
                      <h3 className="text-2xl font-extrabold text-gray-800 mt-2 break-words" title={source.name}>{source.name}</h3>
                    </div>

                    <div className="flex items-center gap-3">
                      {/* Hidden file input */}
                      <label className={`px-4 py-2.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold rounded-xl border border-indigo-200 transition flex items-center gap-2 text-xs cursor-pointer ${
                        uploadingSourceId === source.id ? 'opacity-50 pointer-events-none' : ''
                      }`}>
                        <Upload className="w-3.5 h-3.5" />
                        {uploadingSourceId === source.id ? 'Uploading...' : 'Import File (Append/Add)'}
                        <input 
                          type="file" 
                          className="hidden" 
                          accept=".csv,.xlsx,.xls" 
                          onChange={(e) => handleUploadFile(e, source.id)} 
                        />
                      </label>

                      <button
                        onClick={() => handleDeleteSource(source.id)}
                        className="p-2.5 bg-red-50 hover:bg-red-100 text-red-600 rounded-xl transition border border-red-200"
                        title="Delete Report Source"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {/* Files List under this Source */}
                  <div className="space-y-4">
                    <span className="block text-[11px] font-black text-gray-400 uppercase tracking-wider">Active Monthly Data Files</span>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                      {source.files && source.files.map((file) => (
                        <div key={file.id} className="bg-gray-50/70 border border-gray-150/70 p-5 rounded-2xl flex flex-col justify-between hover:bg-white hover:shadow-sm hover:border-gray-200 transition">
                          <div>
                            <div className="flex justify-between items-start mb-3">
                              <span className="bg-white border border-gray-200 px-2 py-0.5 rounded text-[9px] font-mono font-bold text-gray-500">
                                {file.fileType.toUpperCase()}
                              </span>
                              <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider flex items-center gap-1 ${
                                file.normalizationStatus === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                                file.normalizationStatus === 'FAILED' ? 'bg-red-100 text-red-700' : 'bg-yellow-100 text-yellow-700 animate-pulse'
                              }`}>
                                {file.normalizationStatus === 'COMPLETED' ? (
                                  <>
                                    <CheckCircle className="w-2.5 h-2.5" />
                                    Active
                                  </>
                                ) : file.normalizationStatus}
                              </span>
                            </div>

                            <h4 className="font-bold text-gray-800 text-sm truncate mb-2" title={file.fileName}>
                              {file.fileName}
                            </h4>

                            <div className="text-[10px] text-gray-400 space-y-1 font-semibold">
                              <p className="flex items-center gap-1">
                                <Calendar className="w-3 h-3 text-gray-300" />
                                <span>Imported: {new Date(file.createdAt).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })}</span>
                              </p>
                              <p className="flex items-center gap-1">
                                <Database className="w-3 h-3 text-gray-300" />
                                <span>Rows: {file.processedRows} / {file.totalRows}</span>
                              </p>
                              <p className="flex items-center gap-1">
                                <FileText className="w-3 h-3 text-gray-300" />
                                <span>Size: {formatBytes(file.size)}</span>
                              </p>
                            </div>
                          </div>

                          <div className="mt-4 flex gap-2 border-t border-gray-100 pt-3.5">
                            <Link 
                              to={`/files/${file.id}`}
                              className="flex-1 py-2 bg-white hover:bg-indigo-50 border border-indigo-150 text-indigo-600 text-center font-bold rounded-lg text-[10px] transition flex items-center justify-center gap-1"
                            >
                              <Eye className="w-3 h-3" /> View Data
                            </Link>
                            <label 
                              className="px-3 py-2 bg-white hover:bg-indigo-50 border border-indigo-150 text-indigo-600 font-bold rounded-lg text-[10px] transition flex items-center justify-center cursor-pointer gap-0.5"
                              title="Replace/Overwrite File"
                            >
                              🔄 Replace
                              <input 
                                type="file" 
                                className="hidden" 
                                accept=".csv,.xlsx,.xls" 
                                onChange={(e) => handleReplaceFile(e, file.id)} 
                              />
                            </label>
                            <button 
                              onClick={() => handleDeleteFile(file.id)}
                              className="px-3 py-2 bg-white hover:bg-red-50 border border-red-100 text-red-500 font-bold rounded-lg text-[10px] transition flex items-center justify-center"
                              title="Delete File Data"
                            >
                              <Trash2 className="w-3 h-3" />
                            </button>
                          </div>
                        </div>
                      ))}

                      {(!source.files || source.files.length === 0) && (
                        <div className="col-span-full py-8 text-center text-gray-400 border-2 border-dashed border-gray-200 rounded-2xl flex flex-col items-center justify-center gap-1">
                          <AlertCircle className="w-5 h-5 text-gray-300" />
                          <span className="text-xs font-semibold">No active monthly data files uploaded.</span>
                          <span className="text-[10px] text-gray-400 font-medium">Click "Import File" above to start uploading data.</span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Add Report Source Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/45 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-200">
          <div className="bg-white p-8 rounded-[2rem] w-full max-w-md shadow-2xl border border-gray-100 animate-in zoom-in-95 duration-200">
            <h3 className="text-2xl font-black text-gray-900 tracking-tight mb-5 flex items-center gap-2">
              <Sparkles className="w-6 h-6 text-indigo-600" />
              Create Report Source
            </h3>
            
            <form onSubmit={handleCreateSource} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Display Name</label>
                <input 
                  type="text" 
                  value={newSource.name}
                  onChange={e => {
                    const name = e.target.value;
                    const internalKey = name.toUpperCase().replace(/[^A-Z0-9_]/g, '_');
                    setNewSource({ ...newSource, name, internalKey });
                  }}
                  className="w-full px-4 py-3 rounded-2xl bg-gray-50 border border-gray-250 outline-none text-sm font-semibold focus:border-indigo-400"
                  placeholder="e.g. Bhopal Offtake"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Internal Key (System Identifier)</label>
                <input 
                  type="text" 
                  value={newSource.internalKey}
                  onChange={e => setNewSource({ ...newSource, internalKey: e.target.value.toUpperCase() })}
                  className="w-full px-4 py-3 rounded-2xl bg-gray-50 border border-gray-250 outline-none text-sm font-semibold font-mono focus:border-indigo-400"
                  placeholder="e.g. BHOPAL_OFFTAKE"
                  required
                />
                <span className="text-[10px] text-gray-400 font-medium block mt-1">Exposed internally in formulas and templates. Alphanumeric only.</span>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Table Number (Sort Order)</label>
                <input 
                  type="number" 
                  value={newSource.tableNumber}
                  onChange={e => setNewSource({ ...newSource, tableNumber: e.target.value })}
                  className="w-full px-4 py-3 rounded-2xl bg-gray-50 border border-gray-250 outline-none text-sm font-semibold focus:border-indigo-400"
                  placeholder="e.g. 1"
                />
                <span className="text-[10px] text-gray-400 font-medium block mt-1">Leave empty to auto-sequence.</span>
              </div>

              <div className="flex justify-end gap-3 mt-6">
                <button 
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-5 py-2.5 bg-gray-150 hover:bg-gray-250 text-gray-700 font-bold rounded-xl text-xs transition"
                >
                  Cancel
                </button>
                <button 
                  type="submit"
                  className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl text-xs transition shadow-md shadow-indigo-100"
                >
                  Create Source
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Files;
