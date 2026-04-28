import { ErrorResponse } from '../types';
import { 
  getUserFriendlyErrorMessage, 
  getErrorDetails, 
  requiresSupportContact,
  getSupportContact 
} from '../utils/errorHandler';

const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081';

/**
 * HTTP Client Interface
 * 
 * Base client for all API calls. Features:
 * - Automatic Bearer token injection
 * - Error response parsing
 * - User-friendly error messages
 * - Support contact for technical errors (5xx)
 */
interface HttpClient {
  request<T>(endpoint: string, options?: RequestInit, showNotification?: (message: string) => void): Promise<T>;
  get<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T>;
  post<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  put<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  patch<T>(endpoint: string, body?: any, showNotification?: (message: string) => void): Promise<T>;
  delete<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T>;
}

/**
 * HTTP Client Implementation
 */
class HttpClientImpl implements HttpClient {

  private readonly baseUrl: string;

  constructor(baseUrl: string = BACKEND_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  /**
   * Make HTTP request with authentication
   */
  async request<T>(
    endpoint: string,
    options: RequestInit = {},
    showNotification?: (message: string) => void
  ): Promise<T> {
    const token = localStorage.getItem('accessToken');
    
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    // Add Authorization header if token exists
    if (token) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
    }

    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, {
        ...options,
        headers,
      });

      if (!response.ok) {
        // Parse error response
        const errorResponse = await this.parseErrorResponse(response);
        const userMessage = await getUserFriendlyErrorMessage(response, errorResponse);
        
        // Log technical details (not shown to user)
        const errorDetails = await getErrorDetails(response);
        console.error('API Error:', errorDetails);

        // Show notification if handler provided
        if (showNotification) {
          let message = userMessage;
          
          // Add support contact for technical errors
          if (requiresSupportContact(response.status)) {
            message += ` Contact support at ${getSupportContact()}`;
          }
          
          showNotification(message);
        }

        // Throw error with user-friendly message
        const error = new Error(userMessage);
        (error as any).status = response.status;
        (error as any).errorResponse = errorResponse;
        throw error;
      }

      return await response.json();
    } catch (error: any) {
      // Re-throw if it's already our formatted error
      if (error.status && error.errorResponse) {
        throw error;
      }

      // Handle network errors
      if (error instanceof TypeError && error.message.includes('fetch')) {
        const networkError = new Error('Network error. Please check your connection.');
        if (showNotification) {
          showNotification(networkError.message);
        }
        throw networkError;
      }

      // Re-throw other errors
      throw error;
    }
  }

  /**
   * Parse error response from backend
   */
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

  /**
   * GET request
   */
  get<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' }, showNotification);
  }

  /**
   * POST request
   */
  post<T>(
    endpoint: string,
    body?: any,
    showNotification?: (message: string) => void
  ): Promise<T> {
    return this.request<T>(
      endpoint,
      {
        method: 'POST',
        body: body ? JSON.stringify(body) : undefined,
      },
      showNotification
    );
  }

  /**
   * PUT request
   */
  put<T>(
    endpoint: string,
    body?: any,
    showNotification?: (message: string) => void
  ): Promise<T> {
    return this.request<T>(
      endpoint,
      {
        method: 'PUT',
        body: body ? JSON.stringify(body) : undefined,
      },
      showNotification
    );
  }

  /**
   * PATCH request
   */
  patch<T>(
    endpoint: string,
    body?: any,
    showNotification?: (message: string) => void
  ): Promise<T> {
    return this.request<T>(
      endpoint,
      {
        method: 'PATCH',
        body: body ? JSON.stringify(body) : undefined,
      },
      showNotification
    );
  }

  /**
   * DELETE request
   */
  delete<T>(endpoint: string, showNotification?: (message: string) => void): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' }, showNotification);
  }
}

// Export singleton instance
export const httpClient: HttpClient = new HttpClientImpl();
