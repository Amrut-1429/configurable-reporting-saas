import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Auth from './pages/Auth';
import Dashboard from './pages/Dashboard';
import Reports from './pages/Reports';
import Layout from './components/Layout';
import Files from './pages/Files';
import FileView from './pages/FileView';

import SavedReports from './pages/SavedReports';

const ProtectedRoute = ({ children }) => {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/auth" />;
};

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/auth" element={<Auth />} />
        
        {/* Protected Routes */}
        <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route index element={<Files />} />
          <Route path="reports" element={<Reports />} />
          <Route path="saved" element={<SavedReports />} />
          <Route path="files" element={<Navigate to="/" replace />} />
          <Route path="files/:id" element={<FileView />} />
          <Route path="dashboard" element={<Dashboard />} />
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
