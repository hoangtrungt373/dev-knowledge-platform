import React from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import App from './app/App';
import { setTokenRefreshHandler } from '@shared/api/httpClient';
import { authService } from '@auth/services/authService';
import './styles.css';

// httpClient (shared) doesn't know Keycloak's URLs/clients — authService (the feature that does)
// registers its own silent-refresh implementation here, once, at the composition root.
setTokenRefreshHandler(() => authService.refreshAccessToken());

// A data router (not <BrowserRouter>) so data-router-only hooks work — useBlocker, for the
// unsaved-changes guard. One catch-all route renders the existing <App/>, whose own <Routes> keep
// doing all the real routing: React Router's documented first step for moving an app onto a data
// router without rewriting its route tree.
const router = createBrowserRouter([{ path: '*', element: <App /> }]);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);
