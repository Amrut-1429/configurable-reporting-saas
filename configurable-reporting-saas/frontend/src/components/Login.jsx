import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      const res = await api.post('/auth/login', { username, password });
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('username', res.data.username);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed');
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-[var(--color-background)]">
      <div className="w-full max-w-md rounded-lg bg-[var(--color-surface)] p-8 shadow-xl">
        <h2 className="mb-6 text-center text-3xl font-bold text-white">Login</h2>
        {error && <div className="mb-4 rounded bg-red-500/20 p-3 text-red-400">{error}</div>}
        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm text-gray-300">Username</label>
            <input
              type="text"
              className="w-full rounded border border-gray-600 bg-gray-700 p-2 text-white outline-none focus:border-[var(--color-primary)]"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-gray-300">Password</label>
            <input
              type="password"
              className="w-full rounded border border-gray-600 bg-gray-700 p-2 text-white outline-none focus:border-[var(--color-primary)]"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button
            type="submit"
            className="w-full rounded bg-[var(--color-primary)] py-2 font-semibold text-white transition hover:bg-indigo-500"
          >
            Sign In
          </button>
        </form>
      </div>
    </div>
  );
}
