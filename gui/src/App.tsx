import { useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import NavBar from './components/NavBar';
import PrivateRoute from './components/PrivateRoute';
import GuestRoute from './components/GuestRoute';
import AdminLayout from './components/admin/AdminLayout';
import TagListPage from './pages/admin/TagListPage';
import CategoryListPage from './pages/admin/CategoryListPage';
import InterviewQuestionListPage from './pages/admin/InterviewQuestionListPage';
import InterviewQuestionFormPage from './pages/admin/InterviewQuestionFormPage';
import { NotificationProvider } from './contexts/NotificationContext';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import VerifyOtp from './pages/VerifyOtp';
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
          {/* Guest-only routes — redirect to dashboard if already logged in */}
          <Route path="/login" element={<GuestRoute><Login /></GuestRoute>} />
          <Route path="/signup" element={<GuestRoute><SignUp /></GuestRoute>} />

          {/* Public routes */}
          <Route path="/verify-otp" element={<VerifyOtp />} />
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
            <Route path="interview-questions" element={<InterviewQuestionListPage />} />
            <Route path="interview-questions/new" element={<InterviewQuestionFormPage />} />
            <Route path="interview-questions/:id/edit" element={<InterviewQuestionFormPage />} />
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
