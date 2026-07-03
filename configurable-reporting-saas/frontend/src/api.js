import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8081/api', // Updated to match backend application.properties
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      // Token is likely expired, clear it and redirect to login
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/auth';
    } else if (error.message === 'Network Error' && !error.response) {
      // Spring Security sometimes drops CORS headers on 401/403, resulting in a Network Error
      // We will clear the token and redirect to login just in case
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/auth';
    }
    return Promise.reject(error);
  }
);

export default api;
