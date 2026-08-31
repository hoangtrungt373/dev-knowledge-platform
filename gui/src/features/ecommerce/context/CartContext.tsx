import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import { authService } from '@auth/services/authService';
import { cartApi } from '../api/cartApi';
import { Cart } from '../types';

interface CartContextType {
  cart: Cart | null;
  loading: boolean;
  /** Re-fetches the cart from the server. No-ops (and clears local state) when unauthenticated. */
  refresh: () => Promise<void>;
  addItem: (variantId: number, quantity: number) => Promise<void>;
  updateItem: (variantId: number, quantity: number) => Promise<void>;
  removeItem: (variantId: number) => Promise<void>;
  /** Bulk delete (post-Epic-2 follow-up) — one round trip for however many lines are selected. */
  removeItems: (variantIds: number[]) => Promise<void>;
  /**
   * Adds `newVariantId` and removes `oldVariantId` as one visible update — unlike calling
   * `addItem`/`removeItem` back to back, this only calls `setCart` once, after both backend calls
   * finish, so the UI never renders the transient in-between state where both variants' lines
   * exist at once (which briefly appeared/disappeared as one flickering "new" line — see
   * `CartPage.tsx`'s `handleVariantChange`).
   */
  changeVariant: (oldVariantId: number, newVariantId: number, quantity: number) => Promise<void>;
  /** Clears local cart state without calling the backend — for logout, where the session (and any real server-side cart) is gone regardless. */
  clear: () => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export function useCart(): CartContextType {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within CartProvider');
  }
  return context;
}

/**
 * Global cart state (Epic 2) — one source of truth so the NavBar's item-count badge and every
 * cart-touching page (`ProductDetailPage`'s Add to Cart, `CartPage`, `CheckoutPage`) stay in sync
 * without each holding its own copy, same shape as `NotificationContext`/`StompConnectionContext`.
 * No guest cart (Epic 2 is authenticated-only) — `refresh` no-ops when unauthenticated, mirroring
 * `useFriendRequestsCount`'s own `isAuthenticated()` guard.
 *
 * The initial fetch only runs once, on mount — it does **not** re-run automatically on login,
 * since a client-side `navigate()` after login doesn't remount this provider. `Login.tsx`/
 * `SignUp.tsx`/`AuthCallback.tsx` all call `refresh()` explicitly right after a successful login
 * as a result; `NavBar`'s logout handler calls `clear()` for the same reason in reverse (the
 * subsequent RP-initiated-logout redirect tears everything down anyway, but this avoids a
 * stale badge for the instant before that redirect lands).
 */
export function CartProvider({ children }: { children: ReactNode }): JSX.Element {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async (): Promise<void> => {
    if (!authService.isAuthenticated()) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      const data = await cartApi.getCart();
      setCart(data);
    } catch {
      // Silent — a failed background refresh shouldn't surface a notification; pages that
      // actively fetch the cart (CartPage/CheckoutPage) pass their own showError instead.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const addItem = useCallback(async (variantId: number, quantity: number): Promise<void> => {
    const data = await cartApi.addItem(variantId, quantity);
    setCart(data);
  }, []);

  const updateItem = useCallback(async (variantId: number, quantity: number): Promise<void> => {
    const data = await cartApi.updateItem(variantId, quantity);
    setCart(data);
  }, []);

  const removeItem = useCallback(async (variantId: number): Promise<void> => {
    const data = await cartApi.removeItem(variantId);
    setCart(data);
  }, []);

  const removeItems = useCallback(async (variantIds: number[]): Promise<void> => {
    const data = await cartApi.removeItems(variantIds);
    setCart(data);
  }, []);

  const changeVariant = useCallback(async (oldVariantId: number, newVariantId: number, quantity: number): Promise<void> => {
    await cartApi.addItem(newVariantId, quantity);
    // Only this second response's cart is ever rendered — the addItem response above (which still
    // includes the old line) is deliberately not passed to setCart.
    const data = await cartApi.removeItem(oldVariantId);
    setCart(data);
  }, []);

  const clear = useCallback((): void => {
    setCart(null);
  }, []);

  return (
    <CartContext.Provider value={{ cart, loading, refresh, addItem, updateItem, removeItem, removeItems, changeVariant, clear }}>
      {children}
    </CartContext.Provider>
  );
}
