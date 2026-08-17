import { ErrorResponse } from '@shared/types';
import {
  getUserFriendlyErrorMessage,
  getErrorDetails,
  requiresSupportContact,
  getSupportContact
} from '@shared/utils/errorHandler';
import { STORAGE_KEYS } from '@shared/constants/storage';

// gateway (8080) — the single entry point for all six standalone services now that
// GatewayRoutesConfig proxies to each by path. Was stuck at 8081 (ecommerce-service's own port,
// a pre-extraction leftover) until this default was corrected.
const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

// Silently refreshing an access token means talking to Keycloak's own token endpoint with the
// right client_id — knowledge this shared HTTP layer shouldn't own. authService.ts (the feature
// that actually knows Keycloak's URLs/clients) registers its own refreshAccessToken() here once at
// app startup (see main.tsx) via setTokenRefreshHandler; until registered, a 401 just logs out.
let tokenRefreshHandler: (() => Promise<boolean>) | null = null;

export function setTokenRefreshHandler(handler: () => Promise<boolean>): void {
  tokenRefreshHandler = handler;
}

interface HttpClient {
  request<T>(endpoint: string, options?: RequestInit, showNotification?: (message: string) => void): Promise<T>;
  get<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T>;
  post<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  put<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  patch<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  delete<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T>;
  postForm<T>(endpoint: string, formData: FormData, showNotification?: (message: string) => void): Promise<T>;
}

class HttpClientImpl implements HttpClient {

  private readonly baseUrl: string;

  constructor(baseUrl: string = BACKEND_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  async request<T>(
    endpoint: string,
    options: RequestInit = {},
    showNotification?: (message: string) => void
  ): Promise<T> {
    const token = localStorage.getItem(STORAGE_KEYS.accessToken);

    const headers: Record<string, string> = {
      // Skip Content-Type for FormData — the browser sets it with the multipart boundary
      ...(!(options.body instanceof FormData) && { 'Content-Type': 'application/json' }),
      ...(options.headers as Record<string, string>),
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, {
        ...options,
        headers,
      });

      // On 401, try to silently refresh the token then retry once. Pre-authentication endpoints
      // (no session to expire) are excluded so a failure there surfaces as a normal error to the
      // caller instead of force-clearing storage and redirecting to /login.
      if (response.status === 401 && !endpoint.includes('/auth/refresh') && !endpoint.includes('/auth/login')
          && !endpoint.includes('/auth/register')) {
        const refreshed = tokenRefreshHandler ? await tokenRefreshHandler() : false;
        if (refreshed) {
          const newToken = localStorage.getItem(STORAGE_KEYS.accessToken);
          const retryHeaders = { ...headers };
          if (newToken) retryHeaders['Authorization'] = `Bearer ${newToken}`;
          const retryResponse = await fetch(`${this.baseUrl}${endpoint}`, {
            ...options,
            headers: retryHeaders,
          });
          if (retryResponse.ok) {
            return await this.parseBody<T>(retryResponse);
          }
        }
        // Refresh failed or not available — clear session and redirect to login
        Object.values(STORAGE_KEYS).forEach(k => localStorage.removeItem(k));
        window.location.href = '/login';
        throw new Error('Session expired. Please log in again.');
      }

      if (!response.ok) {
        const errorResponse = await this.parseErrorResponse(response);
        const userMessage = await getUserFriendlyErrorMessage(response, errorResponse);

        const errorDetails = await getErrorDetails(response);
        console.error('API Error:', errorDetails);

        if (showNotification) {
          let message = userMessage;
          if (requiresSupportContact(response.status)) {
            message += ` Contact support at ${getSupportContact()}`;
          }
          showNotification(message);
        }

        const error = new Error(userMessage);
        (error as any).status = response.status;
        (error as any).errorResponse = errorResponse;
        throw error;
      }

      return await this.parseBody<T>(response);
    } catch (error: any) {
      if (error.status && error.errorResponse) throw error;

      if (error instanceof TypeError && error.message.includes('fetch')) {
        const networkError = new Error('Network error. Please check your connection.');
        if (showNotification) showNotification(networkError.message);
        throw networkError;
      }

      throw error;
    }
  }

  private async parseBody<T>(response: Response): Promise<T> {
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      return undefined as unknown as T;
    }
    return response.json();
  }

  private async parseErrorResponse(response: Response): Promise<ErrorResponse | null> {
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const data = await response.json();
        if (data.errorCode && data.status && data.errorMessage) {
          return data as ErrorResponse;
        }
      }
    } catch (error) {
      console.error('Failed to parse error response:', error);
    }
    return null;
  }

  get<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' }, showNotification);
  }

  post<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'POST', body: body ? JSON.stringify(body) : undefined }, showNotification);
  }

  put<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }, showNotification);
  }

  patch<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }, showNotification);
  }

  delete<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' }, showNotification);
  }

  postForm<T>(endpoint: string, formData: FormData, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'POST', body: formData }, showNotification);
  }
}

export const httpClient: HttpClient = new HttpClientImpl();
