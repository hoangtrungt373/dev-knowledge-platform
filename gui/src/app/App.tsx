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
import ProductCategoryListPage from '@ecommerce/pages/ProductCategoryListPage';
import ProductListPage from '@ecommerce/pages/ProductListPage';
import ProductFormPage from '@ecommerce/pages/ProductFormPage';
import ShopPage from '@ecommerce/pages/shop/ShopPage';
import ProductDetailPage from '@ecommerce/pages/shop/ProductDetailPage';
import { NotificationProvider } from '@shared/contexts/NotificationContext';
import { StompConnectionProvider } from '@messaging/context/StompConnectionContext';
import Login from '@auth/pages/Login';
import SignUp from '@auth/pages/SignUp';
import Dashboard from '@auth/pages/Dashboard';
import FriendsPage from '@friends/pages/FriendsPage';
import TasksPage from '@tasks/pages/TasksPage';
import ChatPage from '@chat/pages/ChatPage';
import MessagesPage from '@messaging/pages/MessagesPage';
import AuthCallback from '@auth/pages/AuthCallback';
import AdminLogin from '@auth/pages/AdminLogin';
import AdminAuthCallback from '@auth/pages/AdminAuthCallback';
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
            <Route path="/auth/callback" element={<AuthCallback />} />
            <Route path="/admin/login" element={<AdminLogin />} />
            <Route path="/admin/auth/callback" element={<AdminAuthCallback />} />

            {/* Storefront — genuinely public, unlike every other feature below (backed by
                ecommerce-service's own permitAll /api/v1/public/products/** — browsing works the
                same logged in or out) */}
            <Route path="/shop" element={<ShopPage />} />
            <Route path="/shop/:slug" element={<ProductDetailPage />} />

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

            <Route path="/tasks" element={
              <PrivateRoute>
                <TasksPage />
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
              <Route path="product-categories" element={<ProductCategoryListPage />} />
              <Route path="products" element={<ProductListPage />} />
              <Route path="products/new" element={<ProductFormPage />} />
              <Route path="products/:id/edit" element={<ProductFormPage />} />
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
