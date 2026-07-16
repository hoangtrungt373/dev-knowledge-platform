/**
 * DM Messaging Types
 * Mirrors social-service's dto/messaging/* records (Dm* subset — Phase 1 is DM-only, group/channel
 * types land in Phase 2). Reuses UserSummary from @friends/types rather than duplicating it — same
 * reuse direction the backend itself uses (MessagingMapper `uses = FriendMapper.class`).
 */
import { UserSummary } from '@friends/types';

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE';

/** Attachment metadata for a DM message. Phase 1 never sends one — no upload endpoint exists yet. */
export interface MessageAttachment {
  url: string;
  mimeType: string | null;
  fileName: string | null;
  fileSize: number | null;
}

/** A single message within a 1:1 DM thread. Matches social-service's DmMessageResponse. */
export interface DmMessage {
  id: number;
  threadId: number;
  sender: UserSummary;
  messageType: MessageType;
  content: string | null;
  attachment: MessageAttachment | null;
  createdAt: string;
}

/** One entry in the authenticated user's list of DM conversations. Matches DmThreadResponse. */
export interface DmThread {
  id: number;
  otherUser: UserSummary;
  lastMessageAt: string | null;
}

/** Payload for sending a DM. Phase 1 only ever sets `content` — no attachment support yet. */
export interface SendMessagePayload {
  content: string;
  attachment?: null;
}

/**
 * Error payload delivered to /user/queue/errors when a @MessageMapping handler rejects a message —
 * the WebSocket-side equivalent of the REST error body. Matches social-service's WsErrorResponse.
 */
export interface WsErrorPayload {
  errorCode: string;
  message: string;
}
