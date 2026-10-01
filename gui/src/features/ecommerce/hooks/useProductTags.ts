import { ecommerceApi } from '../api/ecommerceApi';
import { ProductTag } from '../types';
import { StagedTagPickerResult, useStagedTagPicker } from '@shared/hooks/useStagedTagPicker';

type ShowError = (msg: string) => void;

export type UseProductTagsResult = StagedTagPickerResult<ProductTag>;

/**
 * The product-tag picker's state/logic — `@shared/hooks/useStagedTagPicker` (where the actual
 * logic now lives, shared with `@dev-practice`'s problem form) bound to the product-tag API.
 * Originally extracted out of `ProductFormPage.tsx` when that file grew into this app's largest God
 * Component; kept as a wrapper so `ProductFormPage` itself didn't need to change.
 */
export function useProductTags(showError: ShowError): UseProductTagsResult {
  return useStagedTagPicker<ProductTag>({
    loadTags: () => ecommerceApi.listProductTags({ size: 1000, sortBy: 'name', sortDir: 'asc' }, showError)
      .then(page => page.content),
    createTag: name => ecommerceApi.createProductTag({ name }, showError),
  });
}
