import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './app/App';
import { setTokenRefreshHandler } from '@shared/api/httpClient';
import { authService } from '@auth/services/authService';
import './styles.css';

// httpClient (shared) doesn't know Keycloak's URLs/clients — authService (the feature that does)
// registers its own silent-refresh implementation here, once, at the composition root.
setTokenRefreshHandler(() => authService.refreshAccessToken());

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
