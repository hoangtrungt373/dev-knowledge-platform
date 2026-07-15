/**
 * Friend Graph Types
 * Mirrors api's dto/friend/* records and social-service's RelationshipStatus enum.
 */

/**
 * The viewer's computed relationship to another user — not persisted, decides which action
 * (Add Friend / Cancel / Confirm+Delete / Friends menu / Unblock) a row should offer.
 */
export type RelationshipStatus =
  | 'STRANGER'
  | 'REQUEST_SENT'
  | 'REQUEST_RECEIVED'
  | 'FRIENDS'
  | 'BLOCKED';

export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';

/**
 * Minimal public-facing view of a user, embedded in search results, requests, and friend
 * list entries. Matches api's UserSummaryResponse.
 */
export interface UserSummary {
  userUuid: string;
  username: string;
  firstName: string | null;
  lastName: string | null;
  profilePicture: string | null;
  status: string;
}

/** A friend request, in either the incoming or outgoing direction. */
export interface FriendRequest {
  id: number;
  requester: UserSummary;
  addressee: UserSummary;
  status: FriendRequestStatus;
  createdAt: string;
}

/** One entry in the authenticated user's friend list. */
export interface FriendSummary {
  user: UserSummary;
  friendsSince: string;
}

/**
 * One row of a user search — carries the viewer's relationship to the returned user.
 *
 * NOTE: this does NOT carry a friend-request id, so when `relationshipStatus` is
 * `REQUEST_SENT`/`REQUEST_RECEIVED`, a search result cannot directly call accept/reject/cancel
 * (those endpoints take a request id, not a user uuid) — only the dedicated Requests tabs
 * (backed by `FriendRequest[]`, which does have `.id`) can. See `RelationshipActionButton`.
 */
export interface UserSearchResult {
  user: UserSummary;
  relationshipStatus: RelationshipStatus;
  mutualFriendCount: number;
}

export interface FriendListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

export interface UserSearchParams {
  q?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}
