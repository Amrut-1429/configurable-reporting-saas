import { useState } from 'react';
import { UploadCloud, CheckCircle } from 'lucide-react';
import api from '../api';

export default function FileUploader() {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleFileChange = (e) => {
    if (e.target.files[0]) {
      setFile(e.target.files[0]);
      setSuccess(false);
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    const formData = new FormData();
    formData.append('file', file);
    try {
      await api.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setSuccess(true);
      setFile(null);
    } catch (err) {
      console.error(err);
      alert('Upload failed');
    }
    setUploading(false);
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6 pt-10">
      <h2 className="text-2xl font-bold">Upload Raw Data</h2>
      <p className="text-gray-400">Upload your CSV or Excel file containing raw data.</p>
      
      <div className="mt-8 flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-600 bg-[var(--color-surface)] p-12 transition-colors hover:border-[var(--color-primary)] hover:bg-gray-800">
        <UploadCloud size={48} className="mb-4 text-gray-400" />
        <p className="mb-4 text-sm text-gray-300">Drag and drop your file here, or click to browse</p>
        <input type="file" className="hidden" id="file-upload" onChange={handleFileChange} accept=".csv, .xlsx, .xls" />
        <label
          htmlFor="file-upload"
          className="cursor-pointer rounded bg-gray-700 px-4 py-2 font-semibold text-white transition hover:bg-gray-600"
        >
          Select File
        </label>
        {file && <p className="mt-4 text-sm text-[var(--color-secondary)]">{file.name}</p>}
      </div>

      {file && (
        <button
          onClick={handleUpload}
          disabled={uploading}
          className="w-full rounded bg-[var(--color-primary)] py-3 font-semibold text-white transition hover:bg-indigo-500 disabled:opacity-50"
        >
          {uploading ? 'Uploading & Parsing...' : 'Upload File'}
        </button>
      )}

      {success && (
        <div className="flex items-center space-x-2 rounded bg-emerald-500/20 p-4 text-emerald-400">
          <CheckCircle size={20} />
          <span>File uploaded and parsed successfully! You can now map the columns.</span>
        </div>
      )}
    </div>
  );
}
