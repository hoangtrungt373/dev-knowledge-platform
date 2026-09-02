import { useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Box, ThemeProvider, CssBaseline } from '@mui/material';
import NavBar from './NavBar';
import Footer from './Footer';
import PrivateRoute from './PrivateRoute';
import GuestRoute from './GuestRoute';
import AdminLayout from './admin-shell/AdminLayout';
import AdminDashboard from './admin-shell/AdminDashboard';
import AccountLayout from './account-shell/AccountLayout';
import TagListPage from '@content/pages/TagListPage';
import CategoryListPage from '@content/pages/CategoryListPage';
import QuestionAnswerListPage from '@content/pages/QuestionAnswerListPage';
import QuestionAnswerFormPage from '@content/pages/QuestionAnswerFormPage';
import PipelineMetricsPage from '@ai/pages/PipelineMetricsPage';
import EmbeddingsPage from '@ai/pages/EmbeddingsPage';
import ProductCategoryListPage from '@ecommerce/pages/ProductCategoryListPage';
import ProductTagListPage from '@ecommerce/pages/ProductTagListPage';
import ProductAttributeListPage from '@ecommerce/pages/ProductAttributeListPage';
import ProductListPage from '@ecommerce/pages/ProductListPage';
import ProductFormPage from '@ecommerce/pages/ProductFormPage';
import AdminOrderListPage from '@ecommerce/pages/AdminOrderListPage';
import CouponListPage from '@ecommerce/pages/CouponListPage';
import ShopPage from '@ecommerce/pages/shop/ShopPage';
import ProductDetailPage from '@ecommerce/pages/shop/ProductDetailPage';
import CartPage from '@ecommerce/pages/cart/CartPage';
import CheckoutPage from '@ecommerce/pages/checkout/CheckoutPage';
import OrderHistoryPage from '@ecommerce/pages/orders/OrderHistoryPage';
import OrderDetailPage from '@ecommerce/pages/orders/OrderDetailPage';
import AddressBookPage from '@ecommerce/pages/AddressBookPage';
import { NotificationProvider } from '@shared/contexts/NotificationContext';
import { CartProvider } from '@ecommerce/context/CartContext';
import { StompConnectionProvider } from '@messaging/context/StompConnectionContext';
import Login from '@auth/pages/Login';
import SignUp from '@auth/pages/SignUp';
import ProfilePage from '@auth/pages/ProfilePage';
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
        <CartProvider>
        <StompConnectionProvider>
        <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
          <NavBar mode={mode} onToggleMode={toggleMode} />
          <Box sx={{ flexGrow: 1 }}>
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
            {/* /dashboard is kept as a redirect, not removed — AuthCallback.tsx/AdminLogin.tsx/
                Login.tsx/SignUp.tsx/GuestRoute's own default `redirect` prop, and NavBar's brand
                logo all still navigate to this literal path; redirecting here means none of those
                needed to change when Profile moved under the new /account shell below. */}
            <Route path="/dashboard" element={<Navigate to="/account/profile" replace />} />

            {/* Account — the shopper's own Profile + AddressBook + Order History/Detail, sharing
                one sidebar shell (see AccountLayout's own Javadoc-style comment for why it lives
                outside every feature it fronts). Order History/Detail moved here from their own
                top-level /orders routes per request — NavBar's own "Orders" button was removed in
                the same change, folded into the existing "Account" button (same treatment
                Addresses already had: no dedicated NavBar entry of its own). */}
            <Route
              path="/account"
              element={
                <PrivateRoute>
                  <AccountLayout />
                </PrivateRoute>
              }
            >
              <Route path="profile" element={<ProfilePage />} />
              <Route path="addresses" element={<AddressBookPage />} />
              <Route path="orders" element={<OrderHistoryPage />} />
              <Route path="orders/:id" element={<OrderDetailPage />} />
              <Route index element={<Navigate to="profile" replace />} />
            </Route>

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

            {/* Cart & Checkout — Epic 2 is authenticated-only, no guest cart, unlike /shop above */}
            <Route path="/cart" element={
              <PrivateRoute>
                <CartPage />
              </PrivateRoute>
            } />

            <Route path="/checkout" element={
              <PrivateRoute>
                <CheckoutPage />
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
              <Route path="product-tags" element={<ProductTagListPage />} />
              <Route path="product-attributes" element={<ProductAttributeListPage />} />
              <Route path="products" element={<ProductListPage />} />
              <Route path="products/new" element={<ProductFormPage />} />
              <Route path="products/:id/edit" element={<ProductFormPage />} />
              <Route path="orders" element={<AdminOrderListPage />} />
              <Route path="coupons" element={<CouponListPage />} />
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
          </Box>
          <Footer />
        </Box>
        </StompConnectionProvider>
        </CartProvider>
      </NotificationProvider>
    </ThemeProvider>
  );
}

export default App;
