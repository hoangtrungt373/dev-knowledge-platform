/**
 * Types Index
 * Re-exports all types from domain-specific files
 *
 * Usage: import { User, Friend, ErrorResponse } from '../types';
 */

// User & Authentication types
export * from './user.types';

// Common/shared types
export * from './common.types';

// Chat types
export * from './chat.types';

// Friend graph types
export * from './friend.types';
