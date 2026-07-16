import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { DmMessage, DmThread } from '../types';

type ShowError = (msg: string) => void;

export interface ThreadListParams {
  page?: number;
  size?: number;
}

export interface MessageListParams {
  page?: number;
  size?: number;
}

/**
 * REST wrapper for social-service's DmApi — history/list only. Sending a live message goes over
 * STOMP (see api/socket.ts); sendMessage here exists for parity with the backend contract and as a
 * fallback if the socket is ever down, not used by the composer directly in Phase 1.
 */
export const dmApi = {
  listMyThreads(params: ThreadListParams = {}, showError?: ShowError): Promise<PagedResponse<DmThread>> {
    const { page = 0, size = 20 } = params;
    return httpClient.get(`/api/v1/dms?page=${page}&size=${size}`, showError);
  },

  listMessages(
    threadId: number,
    params: MessageListParams = {},
    showError?: ShowError,
  ): Promise<PagedResponse<DmMessage>> {
    const { page = 0, size = 20 } = params;
    return httpClient.get(`/api/v1/dms/${threadId}/messages?page=${page}&size=${size}`, showError);
  },

  sendMessage(recipientUuid: string, content: string, showError?: ShowError): Promise<DmMessage> {
    return httpClient.post(`/api/v1/dms/${recipientUuid}/messages`, { content }, showError);
  },
};
