# User Stories — Chat Feature (MVP)

Chat lives in `social-service` as new packages alongside the existing friend graph (see
`social-service/CLAUDE.md`), not as a separate module. This document captures the first-phase user
stories only — reactions, threads/replies, mentions, presence, read receipts, voice/video, and bots
are explicitly deferred to later phases (see "Deferred / out of scope" at the bottom).

## Key decisions locked for this phase

- **Channel model:** groups contain channels (Discord-style), not flat groups. A DM is a separate,
  lightweight thread — not modeled as a 2-member group.
- **DM access:** requires an accepted `Friendship` between the two users.
- **Group membership:** open add — an owner/admin can add any user directly, no friendship
  required.
- **Channel creation:** owner/admin only.
- **Blocking (`UserBlock`):** gates DMs only (US-2, US-3). It has **no effect** inside a shared
  group/channel — a block does not hide messages or filter membership there. Matches how
  Discord/Teams treat a block as a personal DM filter, not a group-wide one.
- **Attachments (MVP types):** image, generic file. Text and rich-media message types share the
  same underlying model across DMs and channels.

## Epic: 1:1 Direct Messaging

**US-1 — Start a DM by sending the first message**
As a user, I want to send a message to a friend I haven't chatted with before, so that a DM
conversation starts automatically without a separate "create chat" step.
- Given User A and User B have an accepted `Friendship`
- When A sends B a first message
- Then a DM thread is created lazily and the message is stored in it
- If a thread already exists between them, the message is appended — no duplicate thread is created

**US-2 — Non-friends cannot DM each other**
As a user, I want messaging blocked between non-friends, so the friend-gated model is enforced, not
just a UI suggestion.
- Given A and B do not have an accepted `Friendship`
- When A tries to message B
- Then the request is rejected — no thread or message is created

**US-3 — Blocking stops messaging both ways**
As a user, I want a block to fully stop new messages, so a blocked relationship can't be worked
around via DM.
- Given A has blocked B (or vice versa)
- Then neither can send the other a new message
- Rejected using the same non-distinguishable error already used for other blocked-lookup cases
  (never reveals "you're blocked")
- Prior history, if any, stays intact and readable

**US-4 — Send a text message**
As a user, I want to send plain text to a friend, so we can converse.
- Non-empty content; sender, thread, and timestamp recorded
- Appears in both participants' history

**US-5 — Send an image/file attachment**
As a user, I want to share an image or file in a DM, so I'm not limited to text.
- MVP types: image, generic file; stored via MinIO, message references it
- **TBD:** can one message carry text *and* an attachment together, or are they mutually exclusive?
- **TBD:** max file size / max text length for MVP

**US-6 — View my list of DM conversations**
As a user, I want a list of my DMs sorted by recent activity, so I can navigate between
conversations.
- Sorted by last-message timestamp, descending
- Each entry shows the other participant and a snippet of the last message

**US-7 — View a DM's message history**
As a user, I want to scroll through a conversation's past messages, so I can catch up.
- Chronological, paginated (cursor-based, "load older" on scroll up)
- Only the two participants can read it — authorization enforced

## Epic: Group & Channel Messaging

**US-8 — Create a group**
As a user, I want to create a new group, so I have a space for a multi-user conversation.
- Creator becomes **owner** and is auto-added as the first member; group requires a name

**US-9 — Add a member to a group**
As an owner/admin, I want to add any user to my group, so membership isn't limited to friends.
- Only owner/admin can add (open add, no friendship check)
- Added user gets **member** role; adding an existing member is a no-op, not a duplicate

**US-10 — Remove a member from a group**
As an owner/admin, I want to remove a member, so I can manage access.
- Removed member loses access to all channels in the group immediately
- Default assumption (revisit if wrong): admins can remove members but not other admins or the
  owner; only the owner can remove an admin

**US-11 — Leave a group**
As a member (including an admin), I want to leave voluntarily.
- **TBD:** owner leaving — ownership transfer is out of MVP scope; needs an explicit decision
  before implementation (e.g. block it, auto-promote the longest-tenured admin, or require deleting
  the group instead)

**US-12 — Promote/demote a member's role**
As an owner, I want to promote a member to admin or demote an admin back to member, so I can
delegate management.
- Only the owner changes roles in MVP; the owner role itself is not reassignable yet (no ownership
  transfer — see US-11)

**US-13 — Create a channel in a group**
As an owner/admin, I want to create a text channel, so members can organize conversations by topic.
- Owner/admin only; channel name unique within the group
- All group members can see every channel by default — no private/restricted channels in MVP

**US-14 — Send a text message in a channel**
As a group member, I want to post text in a channel I have access to.
- Only current group members can post

**US-15 — Send an attachment in a channel**
As a group member, I want to share an image/file in a channel — same rules as DM attachments
(US-5).

**US-16 — View my list of groups**
As a user, I want to see the groups I belong to, so I can navigate between them.

**US-17 — View channels in a group**
As a group member, I want to see the group's channel list, so I can pick where to read/post.

**US-18 — View a channel's message history**
As a group member, I want to scroll a channel's past messages.
- Only current members can view; paginated, chronological
- Access is revoked immediately on removal (ties to US-10)

## Deferred / out of scope for this phase

- Reactions, replies/threads, edit/delete, message forwarding, pinned/starred messages
- @mentions, full-text search across history
- Presence (online/offline/last-seen), typing indicators, read receipts/delivery status
- Private/restricted channels within a group
- Ephemeral/disappearing messages
- Voice/video calls, screen share
- Chat-integrated AI assistant (bot backed by `ai-service`'s RAG pipeline)
