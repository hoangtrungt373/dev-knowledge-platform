import { httpClient } from './httpClient';
import {
  FriendListParams,
  FriendRequest,
  FriendSummary,
  PagedResponse,
  UserSummary,
} from '../types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

/**
 * Wraps `/api/v1/friends/**` (see api's FriendApi.java). User search lives on `userApi`
 * instead — it's grouped under `/api/v1/users` on the backend, alongside other profile endpoints.
 */
export const friendApi = {
  sendRequest(addresseeUuid: string, showError?: ShowError): Promise<FriendRequest> {
    return httpClient.post(`/api/v1/friends/requests/${addresseeUuid}`, undefined, showError);
  },

  acceptRequest(requestId: number, showError?: ShowError): Promise<FriendRequest> {
    return httpClient.post(`/api/v1/friends/requests/${requestId}/accept`, undefined, showError);
  },

  rejectRequest(requestId: number, showError?: ShowError): Promise<FriendRequest> {
    return httpClient.post(`/api/v1/friends/requests/${requestId}/reject`, undefined, showError);
  },

  cancelRequest(requestId: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/friends/requests/${requestId}`, showError);
  },

  listIncomingRequests(params: FriendListParams, showError?: ShowError): Promise<PagedResponse<FriendRequest>> {
    return httpClient.get(
      `/api/v1/friends/requests/incoming${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  listOutgoingRequests(params: FriendListParams, showError?: ShowError): Promise<PagedResponse<FriendRequest>> {
    return httpClient.get(
      `/api/v1/friends/requests/outgoing${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  listFriends(params: FriendListParams, showError?: ShowError): Promise<PagedResponse<FriendSummary>> {
    return httpClient.get(
      `/api/v1/friends${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  unfriend(userUuid: string, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/friends/${userUuid}`, showError);
  },

  block(userUuid: string, showError?: ShowError): Promise<void> {
    return httpClient.post(`/api/v1/friends/blocks/${userUuid}`, undefined, showError);
  },

  unblock(userUuid: string, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/friends/blocks/${userUuid}`, showError);
  },

  listBlockedUsers(params: FriendListParams, showError?: ShowError): Promise<PagedResponse<UserSummary>> {
    return httpClient.get(
      `/api/v1/friends/blocks${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },
};
