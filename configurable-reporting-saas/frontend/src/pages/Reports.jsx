import { useState, useEffect } from 'react';
import api from '../api';
import { 
  Plus, 
  Trash2, 
  Save, 
  FileSpreadsheet, 
  FileText, 
  Database, 
  Sparkles, 
  Layers, 
  Calendar, 
  TrendingUp, 
  PlusCircle, 
  Check, 
  AlertCircle,
  HelpCircle,
  Settings,
  GitBranch,
  Filter
} from 'lucide-react';

export default function Reports() {
  const [sources, setSources] = useState([]);
  const [selectedFileId, setSelectedFileId] = useState('');
  
  // Workspace and advanced flags
  const [selectedWorkspace, setSelectedWorkspace] = useState('Default Workspace');
  const [layoutType, setLayoutType] = useState('PIVOT'); // PIVOT, SUMMARY_TABLE
  const [advancedMode, setAdvancedMode] = useState(false);
  
  // Sheet structure loaded from file mapping
  const [sheetsAndColumns, setSheetsAndColumns] = useState({});
  const [activeSheet, setActiveSheet] = useState('');

  // Pivot configuration state
  const [pivotRows, setPivotRows] = useState([]);
  const [pivotCols, setPivotCols] = useState([]);
  const [pivotVals, setPivotVals] = useState([]);
  
  // Calculated fields lists
  const [formulas, setFormulas] = useState([]);
  const [columnFormulas, setColumnFormulas] = useState([]);
  
  // Dynamic & static filters list
  const [filters, setFilters] = useState([]);

  // Relationships applied in report
  const [reportRelations, setReportRelations] = useState([]);
  const [globalRelations, setGlobalRelations] = useState([]);

  // Live preview results
  const [reportData, setReportData] = useState(null);
  const [loading, setLoading] = useState(false);

  // Modals & Saving
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [reportName, setReportName] = useState('');
  const [snapshotMode, setSnapshotMode] = useState(false);
  const [saving, setSaving] = useState(false);

  // Editing state (loaded from saved configuration if any)
  const [isEditing, setIsEditing] = useState(false);
  const [editingId, setEditingId] = useState(null);

  // Relationship Preview Modal States
  const [previewRelationOpen, setPreviewRelationOpen] = useState(false);
  const [previewRelationLoading, setPreviewRelationLoading] = useState(false);
  const [previewRelationData, setPreviewRelationData] = useState(null);
  const [activePreviewRelation, setActivePreviewRelation] = useState(null);

  const handlePreviewRelation = async (rel) => {
    setActivePreviewRelation(rel);
    setPreviewRelationOpen(true);
    setPreviewRelationLoading(true);
    setPreviewRelationData(null);
    try {
      const res = await api.post('/reports/relationships/preview', rel);
      setPreviewRelationData(res.data);
    } catch (err) {
      console.error(err);
      alert('Failed to load relationship preview.');
    } finally {
      setPreviewRelationLoading(false);
    }
  };

  // Form states for adding items
  const [newFormula, setNewFormula] = useState({ name: '', expression: '' });
  const [newColFormula, setNewColFormula] = useState({ name: '', formula: '' });
  const [newFilter, setNewFilter] = useState({ field: '', operator: 'EQUALS', value: '' });
  const [newRelation, setNewRelation] = useState({ sourceSheet: '', sourceField: '', targetSheet: '', targetField: '', joinType: 'LEFT' });

  // Inline Field Action Menu
  const [activeFieldMenu, setActiveFieldMenu] = useState(null); // { sheet, field }

  const handleFieldClick = (sheet, field) => {
    if (activeFieldMenu && activeFieldMenu.sheet === sheet && activeFieldMenu.field === field) {
      setActiveFieldMenu(null);
    } else {
      setActiveFieldMenu({ sheet, field });
    }
  };

  const handleAddFieldToZone = (sheet, field, zone) => {
    const fullField = `${sheet}.${field}`;
    if (zone === 'rows') {
      if (!pivotRows.includes(fullField)) {
        setPivotRows(prev => [...prev, fullField]);
      }
    } else if (zone === 'columns') {
      if (!pivotCols.includes(fullField)) {
        setPivotCols(prev => [...prev, fullField]);
      }
    } else if (zone === 'measures') {
      const fieldType = classifyField(field);
      const agg = fieldType === 'numeric' ? 'SUM' : 'COUNT';
      if (!pivotVals.some(v => v.field === fullField)) {
        setPivotVals(prev => [...prev, { field: fullField, aggregation: agg }]);
      }
    } else if (zone === 'filters') {
      if (!filters.some(f => f.field === fullField)) {
        setFilters(prev => [...prev, { field: fullField, operator: 'EQUALS', value: '' }]);
      }
    }
    setActiveFieldMenu(null);
  };

  // Field classifier helper for Smart Field Categorization
  const classifyField = (fieldName) => {
    if (!fieldName) return 'text';
    const lower = fieldName.toLowerCase().replace(/_/g, ' ').replace(/-/g, ' ').trim();
    if (lower.includes('date') || lower.includes('dt') || lower === 'day' || lower === 'month' || lower === 'year') {
      return 'date';
    }
    if (lower === 'amount' || lower === 'crm' || lower === 'quantity' || lower === 'qty' || lower === 'tax' || lower === 'labour' || lower.includes('amount') || lower.includes('rate') || lower.includes('price')) {
      return 'numeric';
    }
    if (lower.includes('number') || lower.includes('id') || lower.includes('code') || lower.includes('card') || lower.includes('invoice') || lower.includes('no')) {
      return 'identifier';
    }
    return 'text';
  };

  const fetchReportSources = async () => {
    try {
      const res = await api.get('/files/sources');
      setSources(res.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchGlobalRelationships = async () => {
    try {
      const res = await api.get('/relationships');
      setGlobalRelations(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchWorkspaceSheetsAndColumns = async (workspace) => {
    try {
      const res = await api.get(`/reports/workspace/sheets-columns?workspace=${encodeURIComponent(workspace)}`);
      setSheetsAndColumns(res.data || {});
      const firstSheet = Object.keys(res.data)[0];
      if (firstSheet) {
        setActiveSheet(firstSheet);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchReportSources();
    fetchGlobalRelationships();
    checkForEditingReport();
  }, []);

  useEffect(() => {
    fetchWorkspaceSheetsAndColumns(selectedWorkspace);
  }, [selectedWorkspace, sources]);

  // Debounced auto-preview runner (300 ms)
  useEffect(() => {
    const delayDebounce = setTimeout(() => {
      if (pivotRows.length > 0 || pivotCols.length > 0 || pivotVals.length > 0 || formulas.length > 0 || columnFormulas.length > 0) {
        handleRunReport();
      } else {
        setReportData(null);
      }
    }, 300);

    return () => clearTimeout(delayDebounce);
  }, [pivotRows, pivotCols, pivotVals, filters, reportRelations, formulas, columnFormulas, selectedWorkspace, layoutType]);

  const checkForEditingReport = () => {
    const editData = localStorage.getItem('edit_report_config');
    if (editData) {
      try {
        const report = JSON.parse(editData);
        localStorage.removeItem('edit_report_config');
        setIsEditing(true);
        setEditingId(report.id);
        setReportName(report.name);
        setSnapshotMode(report.snapshotMode);
        
        if (report.file) {
          setSelectedFileId(report.file.id.toString());
        }

        const template = report.template;
        if (template) {
          if (template.workspace) {
            setSelectedWorkspace(template.workspace);
          }
          if (template.reportType) {
            setLayoutType(template.reportType);
          }
          setPivotRows(template.pivotRows || []);
          setPivotCols(template.pivotColumns || []);
          setPivotVals(template.pivotValues || []);
          setFormulas(template.formulas ? template.formulas.map(f => ({ name: f.name, expression: f.formula })) : []);
          setColumnFormulas(template.columnFormulas || []);
          
          if (template.pivotFilters) {
            setFilters(template.pivotFilters);
          }
          if (template.pivotRelationships) {
            setReportRelations(template.pivotRelationships);
          }
        }
      } catch (e) {
        console.error(e);
      }
    }
  };

  const getSourceMeta = (sheetName) => {
    const source = sources.find(s => s.internalKey === sheetName);
    if (source) {
      return {
        tableNumber: source.tableNumber,
        displayName: source.name
      };
    }
    return {
      tableNumber: '',
      displayName: sheetName
    };
  };

  const getFieldDisplayName = (fullField) => {
    if (!fullField) return '';
    const idx = fullField.indexOf('.');
    if (idx === -1) return fullField;
    const sheet = fullField.substring(0, idx);
    const field = fullField.substring(idx + 1);
    const meta = getSourceMeta(sheet);
    return `${meta.displayName} > ${field}`;
  };

  const getAvailableSheets = () => {
    const sheets = new Set();
    if (activeSheet) {
      sheets.add(activeSheet);
    }
    let added = true;
    while (added) {
      added = false;
      reportRelations.forEach(rel => {
        if (sheets.has(rel.sourceSheet) && !sheets.has(rel.targetSheet)) {
          sheets.add(rel.targetSheet);
          added = true;
        }
        if (sheets.has(rel.targetSheet) && !sheets.has(rel.sourceSheet)) {
          sheets.add(rel.sourceSheet);
          added = true;
        }
      });
    }
    return Array.from(sheets);
  };

  // Drag and Drop handlers
  const handleDragStart = (e, sheet, field) => {
    e.dataTransfer.setData('text/plain', JSON.stringify({ sheet, field }));
  };

  const handleDrop = (e, targetZone) => {
    e.preventDefault();
    try {
      const dataStr = e.dataTransfer.getData('text/plain');
      if (!dataStr) return;
      const { sheet, field } = JSON.parse(dataStr);
      const fullField = `${sheet}.${field}`;
      
      if (targetZone === 'rows') {
        if (!pivotRows.includes(fullField)) {
          setPivotRows(prev => [...prev, fullField]);
        }
      } else if (targetZone === 'columns') {
        if (!pivotCols.includes(fullField)) {
          setPivotCols(prev => [...prev, fullField]);
        }
      } else if (targetZone === 'measures') {
        const fieldType = classifyField(field);
        const agg = fieldType === 'numeric' ? 'SUM' : 'COUNT';
        if (!pivotVals.some(v => v.field === fullField)) {
          setPivotVals(prev => [...prev, { field: fullField, aggregation: agg }]);
        }
      } else if (targetZone === 'filters') {
        if (!filters.some(f => f.field === fullField)) {
          setFilters(prev => [...prev, { field: fullField, operator: 'EQUALS', value: '' }]);
        }
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleAddColumnFormula = () => {
    if (!newColFormula.name || !newColFormula.formula) {
      return alert('Please enter both name and formula for the column formula.');
    }
    const exists = columnFormulas.some(f => f.name === newColFormula.name);
    if (exists) {
      return alert('A column formula with this name already exists.');
    }
    setColumnFormulas([...columnFormulas, { ...newColFormula }]);
    setNewColFormula({ name: '', formula: '' });
  };

  const handleAddCalculatedField = () => {
    if (!newFormula.name || !newFormula.expression) {
      return alert('Please enter name and expression');
    }
    const exists = formulas.some(f => f.name === newFormula.name);
    if (exists) {
      return alert('A formula with this name already exists.');
    }
    setFormulas([...formulas, { ...newFormula }]);
    setNewFormula({ name: '', expression: '' });
  };

  const handleAddRelation = () => {
    if (!newRelation.sourceSheet || !newRelation.sourceField || !newRelation.targetSheet || !newRelation.targetField) {
      return alert('Please select source & target sheets and fields.');
    }
    setReportRelations([...reportRelations, { ...newRelation }]);
    setNewRelation({ sourceSheet: '', sourceField: '', targetSheet: '', targetField: '', joinType: 'LEFT' });
  };

  const handleRemoveItem = (list, setList, index) => {
    const next = [...list];
    next.splice(index, 1);
    setList(next);
  };

  const buildRequestPayload = () => {
    if (layoutType === 'SUMMARY_TABLE') {
      return {
        uploadedFileId: null,
        workspace: selectedWorkspace,
        groupBy: pivotRows[0] || 'OVERALL',
        metrics: pivotVals.map(v => `${v.aggregation}_${v.field}`),
        columns: [...pivotRows, ...pivotCols],
        pivotRows: [],
        pivotColumns: [],
        pivotValues: [],
        formulas: formulas.map(f => ({ name: f.name, formula: f.expression })),
        pivotFilters: filters,
        pivotRelationships: reportRelations
      };
    } else {
      return {
        uploadedFileId: null,
        workspace: selectedWorkspace,
        pivotRows,
        pivotColumns: pivotCols,
        pivotValues: pivotVals,
        formulas: formulas.map(f => ({ name: f.name, formula: f.expression })),
        columnFormulas: columnFormulas,
        pivotFilters: filters,
        pivotRelationships: reportRelations
      };
    }
  };

  const handleRunReport = async () => {
    setLoading(true);
    try {
      const res = await api.post('/reports/generate', buildRequestPayload());
      setReportData(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async (format) => {
    try {
      const res = await api.post(`/reports/export?format=${format}`, buildRequestPayload(), {
        responseType: 'blob'
      });
      const ext = format === 'excel' ? 'xlsx' : format;
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${reportName || 'report'}.${ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error(err);
      alert('Export failed.');
    }
  };

  const handleSaveReport = async () => {
    if (!reportName.trim()) return alert('Please enter a report name');
    setSaving(true);
    try {
      const payload = {
        name: reportName,
        snapshotMode,
        file: null,
        template: {
          groupByField: 'PIVOT',
          reportType: layoutType,
          workspace: selectedWorkspace,
          pivotRows,
          pivotColumns: pivotCols,
          pivotValues: pivotVals,
          formulas: formulas.map(f => ({ name: f.name, formula: f.expression })),
          columnFormulas: columnFormulas,
          pivotFilters: filters,
          pivotRelationships: reportRelations
        }
      };

      if (isEditing) {
        await api.put(`/reports/${editingId}`, payload);
        alert('Report configuration updated successfully!');
      } else {
        await api.post('/reports/save', payload);
        alert('Report configuration saved successfully!');
      }

      setSaveModalOpen(false);
      setIsEditing(false);
      setEditingId(null);
    } catch (err) {
      console.error(err);
      alert('Failed to save report configuration.');
    } finally {
      setSaving(false);
    }
  };

  const handleOverrideValueAgg = (idx, newAgg) => {
    const next = [...pivotVals];
    next[idx].aggregation = newAgg;
    setPivotVals(next);
  };

  const renderDropZone = (label, items, setter, targetZoneName) => {
    return (
      <div 
        onDragOver={(e) => { e.preventDefault(); e.currentTarget.classList.add('bg-indigo-50/50'); }}
        onDragLeave={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); }}
        onDrop={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); handleDrop(e, targetZoneName); }}
        className="border-2 border-dashed border-gray-200 rounded-3xl p-5 min-h-[90px] flex flex-col justify-between hover:border-indigo-300 transition bg-white"
      >
        <span className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{label}</span>
        <div className="flex flex-wrap gap-2">
          {items.map((item, idx) => {
            const display = typeof item === 'string' ? getFieldDisplayName(item) : `${getFieldDisplayName(item.field)} (${item.aggregation})`;
            return (
              <div key={idx} className="bg-indigo-50 text-indigo-700 border border-indigo-100 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 shadow-sm">
                <span>{display}</span>
                <button 
                  onClick={() => handleRemoveItem(items, setter, idx)} 
                  className="hover:text-red-500 font-extrabold"
                >
                  ×
                </button>
              </div>
            );
          })}
          {items.length === 0 && (
            <span className="text-gray-400 text-xs italic m-auto">Drag fields here</span>
          )}
        </div>
      </div>
    );
  };

  // True nested Pivot Matrix Table renderer
  const renderPivotMatrixTable = () => {
    if (!reportData || !reportData.data || !reportData.columns) return null;

    const rowDimColumns = reportData.columns.filter(c => c.accessor && c.accessor.startsWith('row_'));
    
    // Extract column prefixes
    const colPrefixes = [];
    reportData.columns.forEach(c => {
      const acc = c.accessor || '';
      const h = c.header || '';
      if (acc.startsWith('cell_')) {
        let prefix = h;
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

    const measures = pivotVals.map(v => {
      const f = v.field;
      return f.includes('.') ? f.substring(f.indexOf('.') + 1) : f;
    });
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
            {reportData.data.map((row, rowIdx) => {
              // Check if this row is a total row
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
                      // Find column accessor for this prefix and measure
                      const col = reportData.columns.find(c => {
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
    <div className="space-y-8 pb-24 max-w-7xl mx-auto">
      <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight flex items-center gap-3">
            <Sparkles className="text-indigo-600 w-9 h-9 animate-pulse" />
            Configurable Report Designer
          </h1>
          <p className="text-gray-500 mt-2 font-medium text-lg">
            Drag-and-drop Pivot Matrix, visual relationships mapping, and instant live previews.
          </p>
        </div>

        <div className="flex gap-3">
          <label className="flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-2xl cursor-pointer text-xs select-none">
            <Settings className="w-4 h-4" />
            Advanced Mode
            <input 
              type="checkbox" 
              className="w-4 h-4 accent-indigo-600 rounded" 
              checked={advancedMode} 
              onChange={(e) => setAdvancedMode(e.target.checked)} 
            />
          </label>
          <button 
            onClick={() => setSaveModalOpen(true)}
            className="px-5 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-2xl transition shadow-lg shadow-indigo-150 flex items-center gap-2 text-sm"
          >
            <Save className="w-4 h-4" />
            Save Template
          </button>
        </div>
      </header>

      {/* Layout Selector Panel */}
      <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm">
        <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Report Layout Style</label>
        <select 
          value={layoutType}
          onChange={(e) => setLayoutType(e.target.value)}
          className="w-3/12 px-4 py-2.5 rounded-xl bg-gray-50 border border-gray-200 outline-none font-bold text-gray-800 text-xs focus:ring-4 focus:ring-indigo-100"
        >
          <option value="PIVOT">📊 Pivot Matrix (Excel Layout)</option>
          <option value="SUMMARY_TABLE">📋 Summary Table (Flat Layout)</option>
        </select>
      </div>

      {/* Grid Workspace */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        
        {/* Left Control Panel */}
        <div className="lg:col-span-4 space-y-6">
          
          {/* Active Sheet Selector */}
          {Object.keys(sheetsAndColumns).length > 0 && (
            <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm">
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                <Database className="text-indigo-500 w-4 h-4" />
                Select Primary Source Table
              </h3>
              <div className="flex flex-wrap gap-2">
                {Object.keys(sheetsAndColumns).map(sheet => {
                  const meta = getSourceMeta(sheet);
                  return (
                    <button
                      key={sheet}
                      onClick={() => setActiveSheet(sheet)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-extrabold transition ${
                        activeSheet === sheet 
                          ? 'bg-indigo-600 text-white shadow shadow-indigo-100' 
                          : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                      }`}
                    >
                      Table {meta.tableNumber} ({meta.displayName})
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Draggable Columns Palette */}
          {activeSheet && (
            <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm">
              <h3 className="text-lg font-bold text-gray-800 mb-2 flex items-center gap-2">
                <Layers className="text-indigo-500 w-5 h-5" />
                Fields Palette
              </h3>
              <p className="text-[11px] text-gray-400 mb-4">
                Drag fields into target pivot zones on the right to configure the matrix layout.
              </p>
              
              <div className="space-y-5 max-h-[36rem] overflow-y-auto pr-1">
                {getAvailableSheets().map(sheet => {
                  const meta = getSourceMeta(sheet);
                  const cols = sheetsAndColumns[sheet] || [];
                  const categorised = {
                    date: cols.filter(c => classifyField(c) === 'date'),
                    numeric: cols.filter(c => classifyField(c) === 'numeric'),
                    identifier: cols.filter(c => classifyField(c) === 'identifier'),
                    text: cols.filter(c => classifyField(c) === 'text')
                  };

                  return (
                    <div key={sheet} className="space-y-3">
                      <h4 className="font-extrabold text-[10px] bg-indigo-50 text-indigo-700 uppercase tracking-wider px-2.5 py-1 rounded-lg">
                        Table {meta.tableNumber} ({meta.displayName}) {sheet === activeSheet ? ' (Primary)' : ' (Joined)'}
                      </h4>
                      
                      {/* Date Fields */}
                      {categorised.date.length > 0 && (
                        <div>
                          <span className="text-[9px] uppercase tracking-wider text-gray-400 font-bold block mb-1">📅 Date Fields</span>
                          <div className="flex flex-wrap gap-1.5">
                            {categorised.date.map(col => (
                              <div key={col} className="relative inline-block">
                                <div
                                  draggable
                                  onDragStart={(e) => handleDragStart(e, sheet, col)}
                                  onClick={() => handleFieldClick(sheet, col)}
                                  className="bg-amber-50/50 hover:bg-amber-50 text-amber-800 border border-amber-100/60 px-3 py-1 rounded-full text-xs font-bold cursor-pointer transition shadow-sm flex items-center gap-1"
                                >
                                  {col} <span className="text-[9px] opacity-60">▼</span>
                                </div>
                                {activeFieldMenu && activeFieldMenu.sheet === sheet && activeFieldMenu.field === col && (
                                  <div className="absolute left-0 mt-1 w-32 bg-white border border-gray-200 rounded-xl shadow-xl z-30 py-1 font-bold text-[10px] text-gray-700">
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'rows')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Rows</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'columns')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Columns</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'measures')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Measures</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'filters')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Filters</button>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Numeric Fields */}
                      {categorised.numeric.length > 0 && (
                        <div>
                          <span className="text-[9px] uppercase tracking-wider text-gray-400 font-bold block mb-1">📈 Numeric Measures (SUM)</span>
                          <div className="flex flex-wrap gap-1.5">
                            {categorised.numeric.map(col => (
                              <div key={col} className="relative inline-block">
                                <div
                                  draggable
                                  onDragStart={(e) => handleDragStart(e, sheet, col)}
                                  onClick={() => handleFieldClick(sheet, col)}
                                  className="bg-emerald-50/50 hover:bg-emerald-50 text-emerald-800 border border-emerald-100/60 px-3 py-1 rounded-full text-xs font-bold cursor-pointer transition shadow-sm flex items-center gap-1"
                                >
                                  {col} <span className="text-[9px] opacity-60">▼</span>
                                </div>
                                {activeFieldMenu && activeFieldMenu.sheet === sheet && activeFieldMenu.field === col && (
                                  <div className="absolute left-0 mt-1 w-32 bg-white border border-gray-200 rounded-xl shadow-xl z-30 py-1 font-bold text-[10px] text-gray-700">
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'rows')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Rows</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'columns')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Columns</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'measures')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Measures</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'filters')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Filters</button>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Identifier Fields */}
                      {categorised.identifier.length > 0 && (
                        <div>
                          <span className="text-[9px] uppercase tracking-wider text-gray-400 font-bold block mb-1">🆔 Identifier Measures (COUNT)</span>
                          <div className="flex flex-wrap gap-1.5">
                            {categorised.identifier.map(col => (
                              <div key={col} className="relative inline-block">
                                <div
                                  draggable
                                  onDragStart={(e) => handleDragStart(e, sheet, col)}
                                  onClick={() => handleFieldClick(sheet, col)}
                                  className="bg-teal-50/50 hover:bg-teal-50 text-teal-800 border border-teal-100/60 px-3 py-1 rounded-full text-xs font-bold cursor-pointer transition shadow-sm flex items-center gap-1"
                                >
                                  {col} <span className="text-[9px] opacity-60">▼</span>
                                </div>
                                {activeFieldMenu && activeFieldMenu.sheet === sheet && activeFieldMenu.field === col && (
                                  <div className="absolute left-0 mt-1 w-32 bg-white border border-gray-200 rounded-xl shadow-xl z-30 py-1 font-bold text-[10px] text-gray-700">
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'rows')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Rows</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'columns')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Columns</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'measures')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Measures</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'filters')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Filters</button>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Text Fields */}
                      {categorised.text.length > 0 && (
                        <div>
                          <span className="text-[9px] uppercase tracking-wider text-gray-400 font-bold block mb-1">🔤 Dimensions</span>
                          <div className="flex flex-wrap gap-1.5">
                            {categorised.text.map(col => (
                              <div key={col} className="relative inline-block">
                                <div
                                  draggable
                                  onDragStart={(e) => handleDragStart(e, sheet, col)}
                                  onClick={() => handleFieldClick(sheet, col)}
                                  className="bg-indigo-50/30 hover:bg-indigo-50 text-indigo-800 border border-indigo-100/50 px-3 py-1 rounded-full text-xs font-bold cursor-pointer transition shadow-sm flex items-center gap-1"
                                >
                                  {col} <span className="text-[9px] opacity-60">▼</span>
                                </div>
                                {activeFieldMenu && activeFieldMenu.sheet === sheet && activeFieldMenu.field === col && (
                                  <div className="absolute left-0 mt-1 w-32 bg-white border border-gray-200 rounded-xl shadow-xl z-30 py-1 font-bold text-[10px] text-gray-700">
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'rows')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Rows</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'columns')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Columns</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'measures')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Measures</button>
                                    <button onClick={() => handleAddFieldToZone(sheet, col, 'filters')} className="w-full text-left px-3 py-1.5 hover:bg-indigo-50 hover:text-indigo-600 transition">➕ Add to Filters</button>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Right Pivot Configuration & Preview Grid */}
        <div className="lg:col-span-8 space-y-6">
          
          {/* Pivot Drag-and-Drop Zones */}
          <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm grid grid-cols-1 md:grid-cols-2 gap-4">
            {renderDropZone('Rows Dimensions', pivotRows, setPivotRows, 'rows')}
            {renderDropZone('Columns Dimensions', pivotCols, setPivotCols, 'columns')}
            
            {/* Measures Zone */}
            <div 
              onDragOver={(e) => { e.preventDefault(); e.currentTarget.classList.add('bg-indigo-50/50'); }}
              onDragLeave={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); }}
              onDrop={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); handleDrop(e, 'measures'); }}
              className="border-2 border-dashed border-gray-200 rounded-3xl p-5 min-h-[90px] flex flex-col justify-between hover:border-indigo-300 transition bg-white"
            >
              <span className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Measures</span>
              <div className="flex flex-wrap gap-3">
                {pivotVals.map((v, idx) => {
                  const displayLabel = getFieldDisplayName(v.field);
                  return (
                    <div key={idx} className="bg-emerald-50 border border-emerald-150 p-2.5 rounded-2xl flex flex-col gap-1.5 shadow-sm text-xs min-w-[140px] relative">
                      <div className="flex justify-between items-center font-bold text-emerald-800">
                        <span className="truncate pr-4" title={displayLabel}>{displayLabel}</span>
                        <button 
                          onClick={() => handleRemoveItem(pivotVals, setPivotVals, idx)}
                          className="text-red-500 hover:text-red-700 font-extrabold text-sm"
                        >
                          ×
                        </button>
                      </div>
                      
                      <div className="flex flex-col gap-0.5">
                        <span className="text-[9px] uppercase tracking-wider text-emerald-600 font-semibold">Aggregation</span>
                        <select
                          value={v.aggregation}
                          onChange={(e) => handleOverrideValueAgg(idx, e.target.value)}
                          className="w-full bg-white border border-emerald-250 text-emerald-800 text-[10px] py-1 px-1.5 rounded-lg outline-none font-bold shadow-sm"
                        >
                          <option value="SUM">SUM</option>
                          <option value="COUNT">COUNT</option>
                          <option value="AVG">AVG</option>
                          <option value="MIN">MIN</option>
                          <option value="MAX">MAX</option>
                          <option value="DISTINCT_COUNT">DISTINCT COUNT</option>
                        </select>
                      </div>
                    </div>
                  );
                })}
                {pivotVals.length === 0 && (
                  <span className="text-gray-400 text-xs italic m-auto">Drag fields here</span>
                )}
              </div>
            </div>

            {/* Filters Zone */}
            <div 
              onDragOver={(e) => { e.preventDefault(); e.currentTarget.classList.add('bg-indigo-50/50'); }}
              onDragLeave={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); }}
              onDrop={(e) => { e.currentTarget.classList.remove('bg-indigo-50/50'); handleDrop(e, 'filters'); }}
              className="border-2 border-dashed border-gray-200 rounded-3xl p-5 min-h-[90px] flex flex-col justify-between hover:border-indigo-300 transition bg-white"
            >
              <span className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Filters Rules</span>
              <div className="flex flex-wrap gap-3">
                {filters.map((f, idx) => {
                  const displayLabel = getFieldDisplayName(f.field);
                  return (
                    <div key={idx} className="bg-amber-50 border border-amber-150 p-2.5 rounded-2xl flex flex-col gap-1.5 shadow-sm text-xs min-w-[180px] relative">
                      <div className="flex justify-between items-center font-bold text-amber-800">
                        <span className="truncate pr-4" title={displayLabel}>{displayLabel}</span>
                        <button 
                          onClick={() => handleRemoveItem(filters, setFilters, idx)}
                          className="text-red-500 hover:text-red-700 font-extrabold text-sm"
                        >
                          ×
                        </button>
                      </div>
                      
                      <div className="grid grid-cols-2 gap-1">
                        <select
                          value={f.operator}
                          onChange={(e) => {
                            const next = [...filters];
                            next[idx].operator = e.target.value;
                            setFilters(next);
                          }}
                          className="bg-white border border-amber-250 text-amber-800 text-[10px] py-1 px-1 rounded outline-none font-bold"
                        >
                          <option value="EQUALS">EQUALS</option>
                          <option value="CONTAINS">CONTAINS</option>
                          <option value="GREATER_THAN">&gt;</option>
                          <option value="LESS_THAN">&lt;</option>
                          <option value="IN">IN</option>
                        </select>
                        <input
                          type="text"
                          value={f.value}
                          onChange={(e) => {
                            const next = [...filters];
                            next[idx].value = e.target.value;
                            setFilters(next);
                          }}
                          placeholder="value"
                          className="bg-white border border-amber-250 text-amber-800 text-[10px] py-1 px-2 rounded outline-none"
                        />
                      </div>
                    </div>
                  );
                })}
                {filters.length === 0 && (
                  <span className="text-gray-400 text-xs italic m-auto">Drag fields here</span>
                )}
              </div>
            </div>
          </div>

          {/* Visual Relationship Designer */}
          {Object.keys(sheetsAndColumns).length > 0 && (
            <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm space-y-4">
              <h3 className="text-lg font-bold text-gray-800 flex items-center gap-2">
                <GitBranch className="text-indigo-500 w-5 h-5" />
                Visual Relationships Mapper
              </h3>
              
              <div className="space-y-4">
                {reportRelations.map((rel, idx) => {
                  const sourceMeta = getSourceMeta(rel.sourceSheet);
                  const targetMeta = getSourceMeta(rel.targetSheet);
                  const exposedFields = sheetsAndColumns[rel.targetSheet] || [];

                  return (
                    <div key={idx} className="bg-gray-50 border border-gray-100 rounded-3xl p-5 shadow-sm hover:border-indigo-200 transition space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3 text-xs font-bold text-gray-700 flex-1">
                          <span className="bg-white border border-gray-200 px-3 py-1.5 rounded-xl shadow-sm">
                            Table {sourceMeta.tableNumber} ({sourceMeta.displayName}) → <span className="text-indigo-600">{rel.sourceField}</span>
                          </span>
                          <span className="text-indigo-400 font-black">=</span>
                          <span className="bg-white border border-gray-200 px-3 py-1.5 rounded-xl shadow-sm">
                            Table {targetMeta.tableNumber} ({targetMeta.displayName}) → <span className="text-purple-600">{rel.targetField}</span>
                          </span>
                          <span className="bg-indigo-100 text-indigo-800 px-2 py-0.5 rounded-md text-[9px]">
                            {rel.joinType} JOIN
                          </span>
                        </div>
                        
                        <div className="flex gap-2">
                          <button 
                            onClick={() => handlePreviewRelation(rel)}
                            className="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-xl transition border border-indigo-100 shadow-sm text-[10px] font-bold"
                          >
                            👁 Preview Join
                          </button>
                          <button 
                            onClick={() => handleRemoveItem(reportRelations, setReportRelations, idx)}
                            className="p-2 bg-red-50 hover:bg-red-100 text-red-600 rounded-xl transition border border-red-100 shadow-sm"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>

                      {/* Expose columns checklist visual helper */}
                      <div className="text-[10px] text-gray-400 font-semibold border-t border-gray-200/50 pt-2 space-y-1">
                        <span className="block text-gray-500 uppercase tracking-wider text-[8px] mb-1">Exposed Fields:</span>
                        <div className="flex flex-wrap gap-x-3 gap-y-1">
                          {exposedFields.map(f => (
                            <span key={f} className="text-emerald-700 flex items-center gap-0.5">
                              ✓ {f}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  );
                })}

                {reportRelations.length === 0 && (
                  <p className="text-xs text-gray-400 text-center py-4 border border-dashed border-gray-200 rounded-xl">
                    No active relationships mapping this reporting cycle.
                  </p>
                )}
              </div>

              {/* Relationship Form */}
              <div className="bg-gray-50/50 p-4 rounded-3xl border border-gray-150 grid grid-cols-1 md:grid-cols-5 gap-3 items-end pt-4">
                <div className="space-y-1 md:col-span-2 text-xs">
                  <span className="block text-gray-400 font-bold">Source Table & Field</span>
                  <div className="grid grid-cols-2 gap-1.5">
                    <select
                      value={newRelation.sourceSheet}
                      onChange={e => setNewRelation({ ...newRelation, sourceSheet: e.target.value, sourceField: '' })}
                      className="w-full px-2 py-1.5 bg-white border border-gray-200 rounded-lg outline-none font-bold"
                    >
                      <option value="">Select Table</option>
                      {Object.keys(sheetsAndColumns).map(s => {
                        const meta = getSourceMeta(s);
                        return <option key={s} value={s}>Table {meta.tableNumber} ({meta.displayName})</option>;
                      })}
                    </select>
                    <select
                      value={newRelation.sourceField}
                      onChange={e => setNewRelation({ ...newRelation, sourceField: e.target.value })}
                      className="w-full px-2 py-1.5 bg-white border border-gray-200 rounded-lg outline-none font-bold"
                    >
                      <option value="">Select Field</option>
                      {(sheetsAndColumns[newRelation.sourceSheet] || []).map(f => <option key={f} value={f}>{f}</option>)}
                    </select>
                  </div>
                </div>

                <div className="space-y-1 text-xs">
                  <span className="block text-gray-400 font-bold">Join Type</span>
                  <select
                    value={newRelation.joinType}
                    onChange={e => setNewRelation({ ...newRelation, joinType: e.target.value })}
                    className="w-full px-2 py-1.5 bg-white border border-gray-200 rounded-lg outline-none font-bold"
                  >
                    <option value="LEFT">Left Join</option>
                    <option value="INNER">Inner Join</option>
                  </select>
                </div>

                <div className="space-y-1 md:col-span-2 text-xs">
                  <span className="block text-gray-400 font-bold">Target Table & Field</span>
                  <div className="grid grid-cols-2 gap-1.5">
                    <select
                      value={newRelation.targetSheet}
                      onChange={e => setNewRelation({ ...newRelation, targetSheet: e.target.value, targetField: '' })}
                      className="w-full px-2 py-1.5 bg-white border border-gray-200 rounded-lg outline-none font-bold"
                    >
                      <option value="">Select Table</option>
                      {Object.keys(sheetsAndColumns).map(s => {
                        const meta = getSourceMeta(s);
                        return <option key={s} value={s}>Table {meta.tableNumber} ({meta.displayName})</option>;
                      })}
                    </select>
                    <select
                      value={newRelation.targetField}
                      onChange={e => setNewRelation({ ...newRelation, targetField: e.target.value })}
                      className="w-full px-2 py-1.5 bg-white border border-gray-200 rounded-lg outline-none font-bold"
                    >
                      <option value="">Select Field</option>
                      {(sheetsAndColumns[newRelation.targetSheet] || []).map(f => <option key={f} value={f}>{f}</option>)}
                    </select>
                  </div>
                </div>

                <button
                  onClick={handleAddRelation}
                  className="md:col-span-5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 rounded-xl text-xs transition"
                >
                  Create Mapping Relationship
                </button>
              </div>
            </div>
          )}

          {/* Calculations Formulator (Advanced Mode Only) */}
          {advancedMode && (
            <div className="bg-white border border-gray-100 p-6 rounded-[2rem] shadow-sm space-y-6 animate-in slide-in-from-top duration-300">
              <h3 className="text-lg font-bold text-gray-800">Calculations Formulator</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                
                {/* Row Formulas */}
                <div className="space-y-3">
                  <span className="block text-xs font-bold text-gray-400 uppercase">Row Formulas</span>
                  <div className="space-y-1.5">
                    {formulas.map((f, idx) => (
                      <div key={idx} className="bg-gray-50 border border-gray-200 px-3 py-2 rounded-xl flex justify-between items-center text-xs">
                        <div>
                          <span className="font-bold text-gray-800">{f.name}</span>
                          <span className="text-gray-500 block text-[10px] mt-0.5">{f.expression}</span>
                        </div>
                        <button onClick={() => handleRemoveItem(formulas, setFormulas, idx)} className="text-red-500 hover:text-red-700 ml-2">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                    {formulas.length === 0 && (
                      <div className="text-gray-400 text-xs py-3 border border-dashed border-gray-200 rounded-xl text-center">No row formulas.</div>
                    )}
                  </div>

                  <div className="bg-gray-50/50 p-4 rounded-2xl border border-gray-100 space-y-3 pt-4">
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <input
                        type="text"
                        placeholder="FieldName (e.g. Net)"
                        value={newFormula.name}
                        onChange={e => setNewFormula({ ...newFormula, name: e.target.value })}
                        className="px-2 py-1.5 border rounded-lg bg-white outline-none"
                      />
                      <input
                        type="text"
                        placeholder="Expr (e.g. [Sale] - [Tax])"
                        value={newFormula.expression}
                        onChange={e => setNewFormula({ ...newFormula, expression: e.target.value })}
                        className="px-2 py-1.5 border rounded-lg bg-white outline-none"
                      />
                    </div>
                    <button onClick={handleAddCalculatedField} className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 rounded-lg text-xs">
                      Add Row Formula
                    </button>
                  </div>
                </div>

                {/* Column Formulas */}
                <div className="space-y-3">
                  <span className="block text-xs font-bold text-gray-400 uppercase">Column Formulas</span>
                  <div className="space-y-1.5">
                    {columnFormulas.map((f, idx) => (
                      <div key={idx} className="bg-gray-50 border border-gray-200 px-3 py-2 rounded-xl flex justify-between items-center text-xs">
                        <div>
                          <span className="font-bold text-gray-800">{f.name}</span>
                          <span className="text-gray-500 block text-[10px] mt-0.5">{f.formula}</span>
                        </div>
                        <button onClick={() => handleRemoveItem(columnFormulas, setColumnFormulas, idx)} className="text-red-500 hover:text-red-700 ml-2">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                    {columnFormulas.length === 0 && (
                      <div className="text-gray-400 text-xs py-3 border border-dashed border-gray-200 rounded-xl text-center">No column formulas.</div>
                    )}
                  </div>

                  <div className="bg-gray-50/50 p-4 rounded-2xl border border-gray-100 space-y-3 pt-4">
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <input
                        type="text"
                        placeholder="ColName (e.g. Growth)"
                        value={newColFormula.name}
                        onChange={e => setNewColFormula({ ...newColFormula, name: e.target.value })}
                        className="px-2 py-1.5 border rounded-lg bg-white outline-none"
                      />
                      <input
                        type="text"
                        placeholder="Expr (e.g. [Jun] - [May])"
                        value={newColFormula.formula}
                        onChange={e => setNewColFormula({ ...newColFormula, formula: e.target.value })}
                        className="px-2 py-1.5 border rounded-lg bg-white outline-none"
                      />
                    </div>
                    <button onClick={handleAddColumnFormula} className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 rounded-lg text-xs">
                      Add Column Formula
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Matrix Live Preview Box */}
          <div className="bg-white border border-gray-100 p-8 rounded-[2rem] shadow-sm space-y-6">
            <div className="flex justify-between items-center">
              <h3 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
                <span>📊</span> Live Pivot Matrix Preview
              </h3>
              {reportData && (
                <div className="flex gap-2">
                  <button 
                    onClick={() => handleExport('excel')}
                    className="p-2.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-xl transition border border-emerald-100 flex items-center gap-1 text-xs font-bold"
                  >
                    <FileSpreadsheet className="w-4 h-4" /> Export Excel
                  </button>
                  <button 
                    onClick={() => handleExport('pdf')}
                    className="p-2.5 bg-red-50 hover:bg-red-100 text-red-700 rounded-xl transition border border-red-100 flex items-center gap-1 text-xs font-bold"
                  >
                    <FileText className="w-4 h-4" /> Export PDF
                  </button>
                </div>
              )}
            </div>

            {loading ? (
              <div className="text-gray-400 text-center py-20 flex flex-col items-center gap-2">
                <div className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                <span className="text-xs font-semibold">Running pivot engine...</span>
              </div>
            ) : reportData && reportData.data && reportData.columns ? (
              // Excel-like pivot matrix rendering when layoutType is PIVOT and columns are specified
              layoutType === 'PIVOT' && pivotCols.length > 0 ? (
                renderPivotMatrixTable()
              ) : (
                // Flat layout table
                <div className="overflow-x-auto rounded-3xl border border-gray-100">
                  <table className="w-full text-left bg-white text-xs border-collapse">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-100">
                        {reportData.columns.map((col, idx) => (
                          <th 
                            key={idx} 
                            className="px-5 py-4 font-bold text-gray-700 border-r border-gray-100/50"
                          >
                            {typeof col === 'string' ? col : (col.header || '')}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {reportData.data.map((row, rowIdx) => {
                        const isGrandTotal = Object.keys(row).some(k => k.startsWith('row_') && (row[k] === 'Total' || row[k] === 'Grand Total'));
                        return (
                          <tr 
                            key={rowIdx} 
                            className={`transition ${isGrandTotal ? 'bg-indigo-50/70 font-extrabold border-y-2 border-indigo-200/50' : 'hover:bg-gray-50/50'}`}
                          >
                            {reportData.columns.map((col, colIdx) => {
                              const accessor = typeof col === 'string' ? col : col.accessor;
                              const val = row[accessor];
                              const displayVal = val === null || val === undefined ? '' : 
                                (typeof val === 'number' ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : val.toString());
                              
                              return (
                                <td 
                                  key={colIdx} 
                                  className="px-5 py-3.5 border-r border-gray-100/30 font-medium text-gray-800"
                                >
                                  {displayVal}
                                </td>
                              );
                            })}
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )
            ) : (
              <div className="text-gray-400 text-center py-20 border border-dashed border-gray-200 rounded-3xl">
                Configure your Rows, Columns, and Measures to render the pivot matrix layout.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Save Template Modal */}
      {saveModalOpen && (
        <div className="fixed inset-0 bg-black/45 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-200">
          <div className="bg-white p-8 rounded-[2rem] w-full max-w-md shadow-2xl border border-gray-100 animate-in zoom-in-95 duration-200">
            <h3 className="text-2xl font-black text-gray-900 tracking-tight mb-4">Save Report Template</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Report Name</label>
                <input 
                  type="text" 
                  value={reportName}
                  onChange={e => setReportName(e.target.value)}
                  className="w-full px-4 py-3 rounded-2xl bg-gray-50 border border-gray-250 outline-none text-sm font-semibold"
                  placeholder="e.g. Monthly Offtake Analysis"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input 
                  type="checkbox" 
                  id="snapMode" 
                  checked={snapshotMode}
                  onChange={e => setSnapshotMode(e.target.checked)}
                  className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
                />
                <label htmlFor="snapMode" className="text-xs font-bold text-gray-600">Save Snapshot Mode (Static Copy)</label>
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button 
                onClick={() => setSaveModalOpen(false)}
                className="px-5 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-xl text-xs transition"
              >
                Cancel
              </button>
              <button 
                onClick={handleSaveReport}
                disabled={saving}
                className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl text-xs transition disabled:opacity-50"
              >
                {saving ? 'Saving...' : 'Save Configuration'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Relationship Join Preview Modal */}
      {previewRelationOpen && (
        <div className="fixed inset-0 bg-black/45 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-200">
          <div className="bg-white p-8 rounded-[2rem] w-full max-w-4xl max-h-[85vh] shadow-2xl border border-gray-100 animate-in zoom-in-95 duration-200 flex flex-col">
            
            <div className="flex justify-between items-center border-b border-gray-100 pb-4 mb-6">
              <div>
                <h3 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2">
                  <span>🔗</span> Relationship Join Preview
                </h3>
                {activePreviewRelation && (
                  <p className="text-xs text-gray-500 mt-1">
                    {getSourceMeta(activePreviewRelation.sourceSheet).displayName} ({activePreviewRelation.sourceField}) 
                    = {getSourceMeta(activePreviewRelation.targetSheet).displayName} ({activePreviewRelation.targetField})
                  </p>
                )}
              </div>
              
              <button 
                onClick={() => setPreviewRelationOpen(false)}
                className="p-1.5 hover:bg-gray-100 rounded-xl transition text-gray-400 hover:text-gray-600 font-extrabold"
              >
                ×
              </button>
            </div>

            <div className="flex-1 overflow-y-auto pr-1">
              {previewRelationLoading ? (
                <div className="flex flex-col justify-center items-center py-20 space-y-3">
                  <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-500 border-t-transparent"></div>
                  <span className="text-gray-500 text-xs font-semibold">Analyzing relationship matches...</span>
                </div>
              ) : previewRelationData ? (
                <div className="space-y-6">
                  {/* Summary Counts */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="bg-green-50 border border-green-150 p-4 rounded-2xl">
                      <span className="block text-xs font-bold text-green-700 uppercase tracking-wider mb-1">Matched Records</span>
                      <span className="text-2xl font-black text-green-900">{previewRelationData.matchedCount.toLocaleString()}</span>
                    </div>
                    <div className="bg-amber-50 border border-amber-150 p-4 rounded-2xl">
                      <span className="block text-xs font-bold text-amber-700 uppercase tracking-wider mb-1">Unmatched Records</span>
                      <span className="text-2xl font-black text-amber-900">{previewRelationData.unmatchedCount.toLocaleString()}</span>
                    </div>
                  </div>

                  {/* Sample Data Table */}
                  <div className="space-y-2">
                    <span className="block text-xs font-bold text-gray-400 uppercase tracking-wider">Sample Joined Data (Top 10 Records)</span>
                    <div className="overflow-x-auto border border-gray-150 rounded-2xl">
                      <table className="w-full text-left bg-white text-xs border-collapse">
                        <thead className="bg-gray-50 text-gray-600 font-bold border-b border-gray-150">
                          <tr>
                            {previewRelationData.columns.map((col, idx) => (
                              <th key={idx} className="px-4 py-3 border-r border-gray-150 last:border-r-0 whitespace-nowrap">
                                {col}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 font-medium text-gray-800">
                          {previewRelationData.sampleData.map((row, rIdx) => (
                            <tr key={rIdx} className="hover:bg-gray-50/50 transition">
                              {previewRelationData.columns.map((col, cIdx) => {
                                const val = row[col];
                                return (
                                  <td key={cIdx} className="px-4 py-2.5 border-r border-gray-150 last:border-r-0 whitespace-nowrap">
                                    {val !== null && val !== undefined ? val.toString() : '-'}
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="text-center text-gray-400">No preview data found.</p>
              )}
            </div>

            <div className="border-t border-gray-100 pt-4 mt-6 flex justify-end">
              <button 
                onClick={() => setPreviewRelationOpen(false)}
                className="px-6 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-xl transition text-xs"
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
