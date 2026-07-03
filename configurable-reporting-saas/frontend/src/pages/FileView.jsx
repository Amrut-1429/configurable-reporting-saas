import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api';

const FileView = () => {
  const { id } = useParams();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    fetchData(page);
  }, [id, page]);

  const fetchData = async (pageNum) => {
    setLoading(true);
    try {
      const res = await api.get(`/files/${id}/data?page=${pageNum}&size=50`);
      setData(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { key: 'transactionDate', label: 'Date' },
    { key: 'invoiceNumber', label: 'Invoice Number' },
    { key: 'locationName', label: 'Location' },
    { key: 'productNumber', label: 'Product Part #' },
    { key: 'productName', label: 'Product Name' },
    { key: 'saleAmount', label: 'Sale Amount' },
    { key: 'purchaseAmount', label: 'Purchase Amount' },
    { key: 'expenseAmount', label: 'Expense Amount' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'taxAmount', label: 'Tax Amount' },
  ];

  return (
    <div className="space-y-8 pb-20">
      <header className="flex justify-between items-end mb-8">
        <div>
          <Link to="/files" className="text-indigo-600 hover:text-indigo-800 font-bold mb-2 inline-block">
            &larr; Back to Files
          </Link>
          <h1 className="text-4xl font-black text-gray-900 tracking-tight">Data Explorer</h1>
          <p className="text-gray-500 mt-2 font-medium text-lg">
            Viewing {totalElements.toLocaleString()} normalized records from file ID #{id}
          </p>
        </div>
      </header>

      {loading && data.length === 0 ? (
        <div className="flex justify-center p-12">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-indigo-500 border-t-transparent"></div>
        </div>
      ) : (
        <div className="bg-white/80 backdrop-blur-xl border border-white p-6 rounded-[2rem] shadow-sm">
          <div className="overflow-x-auto rounded-2xl border border-gray-100 shadow-sm mb-6 max-h-[70vh]">
            <table className="text-left bg-white whitespace-nowrap min-w-full">
              <thead className="bg-gray-50 border-b border-gray-100 sticky top-0 z-10">
                <tr>
                  {columns.map((col, index) => (
                    <th 
                      key={col.key} 
                      className={`px-6 py-4 font-bold text-gray-700 text-xs tracking-wider uppercase bg-gray-50 ${index < 3 ? 'sticky left-0 z-20' : ''}`}
                      style={index < 3 ? { left: index * 150 + 'px' } : {}}
                    >
                      {col.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {data.map((row) => (
                  <tr key={row.id} className="hover:bg-indigo-50/30 transition-colors group">
                    {columns.map((col, index) => (
                      <td 
                        key={col.key} 
                        className={`px-6 py-3 font-medium text-gray-700 text-sm ${index < 3 ? 'sticky left-0 bg-white group-hover:bg-indigo-50/30 z-10' : ''}`}
                        style={index < 3 ? { left: index * 150 + 'px' } : {}}
                      >
                        {row[col.key] || '-'}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            
            {data.length === 0 && (
              <div className="p-8 text-center text-gray-500 font-medium">No normalized data available. The file might still be processing or failed normalization.</div>
            )}
          </div>
          
          {/* Pagination Controls */}
          <div className="flex items-center justify-between px-2">
            <button 
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-5 py-2 bg-gray-50 hover:bg-gray-100 disabled:opacity-50 text-gray-700 font-bold rounded-xl transition-all border border-gray-200"
            >
              Previous
            </button>
            <span className="text-gray-500 font-medium bg-gray-50 px-4 py-2 rounded-xl border border-gray-100">
              Page <strong className="text-gray-900">{page + 1}</strong> of <strong className="text-gray-900">{totalPages}</strong>
            </span>
            <button 
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-5 py-2 bg-gray-50 hover:bg-gray-100 disabled:opacity-50 text-gray-700 font-bold rounded-xl transition-all border border-gray-200"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default FileView;
