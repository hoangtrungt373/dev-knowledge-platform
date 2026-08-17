import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { authService } from '@auth/services/authService';

interface Props {
  children: ReactNode;
  redirect?: string;
}

export default function GuestRoute({ children, redirect = '/dashboard' }: Props): JSX.Element {
  const location = useLocation();

  if (authService.isAuthenticated()) {
    // Forward the query string (e.g. ?emailVerified=true from identity-service's sendVerifyEmail
    // redirect) so a still-logged-in user's confirmation toast still fires on whichever page they
    // actually land on, instead of silently dropping it on this redirect.
    return <Navigate to={`${redirect}${location.search}`} replace />;
  }
  return <>{children}</>;
}