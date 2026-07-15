import { PagedResponse } from '@shared/types';
import { UserSearchParams, UserSearchResult } from '../types';
import { User } from '@auth/types';
import { httpClient } from '@shared/api/httpClient';

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

/**
 * Wraps the relationship-enriched user-directory endpoints under `/api/v1/users` (public
 * profile + search) — mirrors the backend's own split: `social-service`'s `UserApi.java` owns
 * these, while `identity-service`'s `UserApi.java` owns pure profile mutation (see
 * `@auth/api/profileApi.ts`).
 */
interface FriendsUserApi {
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User>;
  searchUsers(params: UserSearchParams, showError?: (message: string) => void): Promise<PagedResponse<UserSearchResult>>;
}

export const userApi: FriendsUserApi = {
  getUserById(userUuid: string, showError?: (message: string) => void): Promise<User> {
    return httpClient.get<User>(`/api/v1/users/public/${userUuid}`, showError);
  },

  // Was previously typed as a flat User[] against a bare `?q=` query — didn't match the real
  // backend contract (paginated PagedResponse<UserSearchResultResponse>, which also carries the
  // viewer's relationshipStatus/mutualFriendCount for each result). Fixed as part of building the
  // friend-management UI, which is the first real caller of this endpoint.
  searchUsers(params: UserSearchParams, showError?: (message: string) => void): Promise<PagedResponse<UserSearchResult>> {
    return httpClient.get(
      `/api/v1/users/search${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },
};
