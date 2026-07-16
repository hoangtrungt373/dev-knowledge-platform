import { useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import NavBar from './NavBar';
import PrivateRoute from './PrivateRoute';
import GuestRoute from './GuestRoute';
import AdminLayout from './admin-shell/AdminLayout';
import AdminDashboard from './admin-shell/AdminDashboard';
import TagListPage from '@content/pages/TagListPage';
import CategoryListPage from '@content/pages/CategoryListPage';
import QuestionAnswerListPage from '@content/pages/QuestionAnswerListPage';
import QuestionAnswerFormPage from '@content/pages/QuestionAnswerFormPage';
import PipelineMetricsPage from '@ai/pages/PipelineMetricsPage';
import EmbeddingsPage from '@ai/pages/EmbeddingsPage';
import { NotificationProvider } from '@shared/contexts/NotificationContext';
import { StompConnectionProvider } from '@messaging/context/StompConnectionContext';
import Login from '@auth/pages/Login';
import SignUp from '@auth/pages/SignUp';
import VerifyOtp from '@auth/pages/VerifyOtp';
import Dashboard from '@auth/pages/Dashboard';
import FriendsPage from '@friends/pages/FriendsPage';
import ChatPage from '@chat/pages/ChatPage';
import MessagesPage from '@messaging/pages/MessagesPage';
import AuthCallback from '@auth/pages/AuthCallback';
import AdminLogin from '@auth/pages/AdminLogin';
import { authService } from '@auth/services/authService';
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
        <StompConnectionProvider>
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

            <Route path="/friends" element={
              <PrivateRoute>
                <FriendsPage />
              </PrivateRoute>
            } />

            {/* Chat — full-page layout, NavBar is hidden on these routes */}
            <Route path="/chat" element={
              <PrivateRoute>
                <ChatPage mode={mode} onToggleMode={toggleMode} />
              </PrivateRoute>
            } />
            <Route path="/chat/:sessionId" element={
              <PrivateRoute>
                <ChatPage mode={mode} onToggleMode={toggleMode} />
              </PrivateRoute>
            } />

            {/* Messages — full-page layout, NavBar is hidden on these routes */}
            <Route path="/messages" element={
              <PrivateRoute>
                <MessagesPage mode={mode} onToggleMode={toggleMode} />
              </PrivateRoute>
            } />
            <Route path="/messages/new/:recipientUuid" element={
              <PrivateRoute>
                <MessagesPage mode={mode} onToggleMode={toggleMode} />
              </PrivateRoute>
            } />
            <Route path="/messages/:threadId" element={
              <PrivateRoute>
                <MessagesPage mode={mode} onToggleMode={toggleMode} />
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
              <Route path="question-answers" element={<QuestionAnswerListPage />} />
              <Route path="question-answers/new" element={<QuestionAnswerFormPage />} />
              <Route path="question-answers/:id/edit" element={<QuestionAnswerFormPage />} />
              <Route path="pipeline-metrics" element={<PipelineMetricsPage />} />
              <Route path="embeddings" element={<EmbeddingsPage />} />
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
        </StompConnectionProvider>
      </NotificationProvider>
    </ThemeProvider>
  );
}

export default App;
