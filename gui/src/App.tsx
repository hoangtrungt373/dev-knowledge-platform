import { useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import NavBar from './components/NavBar';
import PrivateRoute from './components/PrivateRoute';
import AdminLayout from './components/admin/AdminLayout';
import TagListPage from './pages/admin/TagListPage';
import CategoryListPage from './pages/admin/CategoryListPage';
import { NotificationProvider } from './contexts/NotificationContext';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import Dashboard from './pages/Dashboard';
import AuthCallback from './pages/AuthCallback';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import { authService } from './services';
import { darkTheme, lightTheme, ThemeMode } from './theme';

function App() {
  const [mode, setMode] = useState<ThemeMode>(() => {
    return (localStorage.getItem('theme-mode') as ThemeMode) ?? 'dark';
  });

  const toggleMode = () => {
    setMode(prev => {
      const next = prev === 'dark' ? 'light' : 'dark';
      localStorage.setItem('theme-mode', next);
      return next;
    });
  };

  return (
    <ThemeProvider theme={mode === 'dark' ? darkTheme : lightTheme}>
      <CssBaseline />
      <NotificationProvider>
        <NavBar mode={mode} onToggleMode={toggleMode} />
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<SignUp />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/admin/login" element={<AdminLogin />} />

          {/* Protected user routes */}
          <Route path="/dashboard" element={
            <PrivateRoute>
              <Dashboard />
            </PrivateRoute>
          } />

          {/* Admin routes — nested under AdminLayout */}
          <Route
            path="/admin"
            element={
              <PrivateRoute requireRole="ADMIN" redirect="/admin/login">
                <AdminLayout />
              </PrivateRoute>
            }
          >
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="tags" element={<TagListPage />} />
            <Route path="categories" element={<CategoryListPage />} />
            <Route index element={<Navigate to="dashboard" replace />} />
          </Route>

          <Route
            path="/"
            element={
              <Navigate
                to={authService.isAuthenticated() ? '/dashboard' : '/login'}
                replace
              />
            }
          />
        </Routes>
      </NotificationProvider>
    </ThemeProvider>
  );
}

export default App;
