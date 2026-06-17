import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { authService } from '../services';

interface Props {
  children: ReactNode;
  redirect?: string;
}

export default function GuestRoute({ children, redirect = '/dashboard' }: Props): JSX.Element {
  if (authService.isAuthenticated()) {
    return <Navigate to={redirect} replace />;
  }
  return <>{children}</>;
}