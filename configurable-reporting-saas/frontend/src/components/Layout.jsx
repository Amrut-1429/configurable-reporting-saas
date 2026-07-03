import { Outlet, Link, useLocation } from 'react-router-dom';

const Layout = () => {
  const location = useLocation();

  const navItems = [
    { name: 'Report Sources', path: '/', icon: '📁' },
    { name: 'Reports', path: '/reports', icon: '📈' },
    { name: 'Saved Reports', path: '/saved', icon: '💾' },
    { name: 'Dashboard', path: '/dashboard', icon: '📊' },
  ];

  return (
    <div className="flex h-screen bg-gray-50/50 backdrop-blur-3xl overflow-hidden font-sans">
      {/* Sidebar */}
      <aside className="w-72 bg-white/70 backdrop-blur-md border-r border-gray-200/50 shadow-2xl z-10 flex flex-col transition-all duration-300">
        <div className="p-8 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 shadow-lg flex items-center justify-center text-white font-bold text-xl">
              N
            </div>
            <h1 className="text-2xl font-black bg-clip-text text-transparent bg-gradient-to-r from-indigo-600 to-purple-600 tracking-tight">NexusBI</h1>
          </div>
        </div>
        
        <nav className="flex-1 mt-8 px-4 space-y-2">
          {navItems.map((item) => (
            <Link
              key={item.name}
              to={item.path}
              className={`flex items-center gap-4 px-4 py-3.5 rounded-2xl transition-all duration-300 group relative overflow-hidden ${
                location.pathname === item.path
                  ? 'bg-gradient-to-r from-indigo-50 to-purple-50/50 text-indigo-700 shadow-sm border border-indigo-100/50'
                  : 'text-gray-600 hover:bg-gray-50/80 hover:text-gray-900'
              }`}
            >
              {location.pathname === item.path && (
                <div className="absolute left-0 top-0 bottom-0 w-1 bg-gradient-to-b from-indigo-500 to-purple-500 rounded-r-full" />
              )}
              <span className="text-xl group-hover:scale-110 transition-transform">{item.icon}</span>
              <span className="font-semibold text-[15px]">{item.name}</span>
            </Link>
          ))}
        </nav>

        <div className="p-6">
          <button 
            onClick={() => { localStorage.removeItem('token'); window.location.href = '/auth'; }}
            className="w-full py-3 px-4 flex items-center justify-center gap-2 bg-gray-100/50 hover:bg-red-50 text-gray-600 hover:text-red-600 font-semibold rounded-2xl transition-all"
          >
            🚪 Logout
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 overflow-y-auto relative bg-[url('https://www.transparenttextures.com/patterns/cubes.png')]">
        <div className="absolute inset-0 bg-gradient-to-br from-indigo-50/40 via-white/40 to-purple-50/40 backdrop-blur-[100px] -z-10" />
        <div className="p-10 max-w-7xl mx-auto w-full animate-in fade-in slide-in-from-bottom-4 duration-700 ease-out">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default Layout;
