import { useState, useEffect } from 'react';
import api from '../api';

const SYSTEM_FIELDS = [
  'LOCATION', 'DATE', 'JOB_CARD', 'INVOICE', 'QUANTITY', 'AMOUNT', 'TAX', 'PRODUCT_NAME', 'PRODUCT_NUMBER', 'PRODUCT_CATEGORY', 'IGNORE'
];

export default function MappingBuilder() {
  const [files, setFiles] = useState([]);
  const [selectedFileId, setSelectedFileId] = useState('');
  const [mappings, setMappings] = useState([]);

  useEffect(() => {
    api.get('/files').then((res) => setFiles(res.data)).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedFileId) {
      api.get(`/files/${selectedFileId}/mapping`).then(res => {
        if (res.data && res.data.mappings) {
          setMappings(res.data.mappings);
        } else {
          setMappings([]);
        }
      }).catch(console.error);
    } else {
      setMappings([]);
    }
  }, [selectedFileId]);

  const handleAddMapping = () => {
    setMappings([...mappings, { excelColumn: '', mappedField: 'IGNORE', dataType: 'STRING' }]);
  };

  const handleMappingChange = (index, field, value) => {
    const newMappings = [...mappings];
    newMappings[index][field] = value;
    setMappings(newMappings);
  };

  const handleSaveMappings = async () => {
    if (!selectedFileId) return alert('Select a file');
    try {
      await api.post(`/files/${selectedFileId}/mapping/confirm`, mappings);
      alert('Mappings confirmed successfully!');
    } catch (err) {
      console.error(err);
      alert('Failed to save mappings');
    }
  };

  return (
    <div className="space-y-6 pb-20">
      <h2 className="text-2xl font-bold">Column Mapping Builder</h2>
      
      <div className="rounded-xl bg-[var(--color-surface)] p-6 shadow-md">
        <div className="mb-6">
          <label className="mb-2 block text-sm text-gray-300">Select File</label>
          <select
            className="w-full md:w-1/2 rounded border border-gray-600 bg-gray-700 p-2 text-white"
            value={selectedFileId}
            onChange={(e) => setSelectedFileId(e.target.value)}
          >
            <option value="">-- Select File --</option>
            {files.map(f => (
              <option key={f.id} value={f.id}>{f.fileName} ({f.normalizationStatus})</option>
            ))}
          </select>
        </div>

        {selectedFileId && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4 font-semibold text-gray-400">
              <div>Excel Column (from file)</div>
              <div>System Meaning</div>
            </div>
            
            {mappings.map((mapping, idx) => (
              <div key={idx} className="grid grid-cols-2 gap-4 items-center">
                <input
                  type="text"
                  placeholder="e.g. Invoice Value"
                  className="rounded border border-gray-600 bg-gray-700 p-2 text-white"
                  value={mapping.excelColumn}
                  onChange={(e) => handleMappingChange(idx, 'excelColumn', e.target.value)}
                />
                <select
                  className="rounded border border-gray-600 bg-gray-700 p-2 text-white"
                  value={mapping.mappedField || 'IGNORE'}
                  onChange={(e) => handleMappingChange(idx, 'mappedField', e.target.value)}
                >
                  <option value="IGNORE">-- IGNORE --</option>
                  {SYSTEM_FIELDS.map(sf => (
                    <option key={sf} value={sf}>{sf}</option>
                  ))}
                </select>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6 flex space-x-4">
          <button
            onClick={handleAddMapping}
            className="rounded bg-gray-700 px-4 py-2 text-sm text-white transition hover:bg-gray-600"
          >
            + Add Field
          </button>
          <button
            onClick={handleSaveMappings}
            className="rounded bg-[var(--color-secondary)] px-4 py-2 font-semibold text-white transition hover:bg-emerald-400"
          >
            Confirm Mappings
          </button>
        </div>
      </div>
    </div>
  );
}
