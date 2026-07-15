import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { authService } from '@auth/services/authService';

interface Props {
  children: ReactNode;
  /** If set, the authenticated user must also have this role. */
  requireRole?: string;
  /** Where to redirect when the check fails. Defaults to '/login'. */
  redirect?: string;
}

/**
 * Wraps a route element and redirects unauthenticated (or insufficiently-
 * privileged) users before the page component ever mounts.
 *
 * Usage in App.tsx:
 *   <Route path="/dashboard"        element={<PrivateRoute><Dashboard /></PrivateRoute>} />
 *   <Route path="/admin/dashboard"  element={<PrivateRoute requireRole="ADMIN" redirect="/admin/login"><AdminDashboard /></PrivateRoute>} />
 */
export default function PrivateRoute({ children, requireRole, redirect = '/login' }: Props): JSX.Element {
  if (!authService.isAuthenticated()) {
    return <Navigate to={redirect} replace />;
  }
  if (requireRole && authService.getRole() !== requireRole) {
    return <Navigate to={redirect} replace />;
  }
  return <>{children}</>;
}
