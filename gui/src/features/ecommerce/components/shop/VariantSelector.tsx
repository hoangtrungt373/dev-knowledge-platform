import { useEffect, useMemo, useState } from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import { ProductVariant } from '../../types';

interface Props {
  variants: ProductVariant[];
  onSelect: (variant: ProductVariant | null) => void;
  /** Pre-fills the selection (e.g. the currently active variant's own attributes) instead of
   * starting blank — used by `CartPage`'s inline variant switcher so the popover opens already
   * showing what's in the cart today, not an empty picker. */
  initialAttributes?: Record<string, string>;
  /** `'stacked'` (default) renders each attribute's label above its chip row — compact, fits
   * `CartPage`'s narrow inline-switcher popover. `'row'` renders the label to the left of one
   * horizontal chip row instead (Shopee-style label/value table) — used by `ProductDetailPage`'s
   * wider layout, where there's room for it. */
  layout?: 'stacked' | 'row';
}

/**
 * Resolves a size/color-style attribute combination to one exact {@link ProductVariant} — unlike
 * the storefront's browse/search facets (which only know "some variant has size M" and "some
 * variant has color Black" independently, per {@code ProductSearchView}'s own documented
 * limitation), this component has the product's real, full variant list, so it can require an
 * exact match across every attribute key rather than an approximation.
 */
export default function VariantSelector({ variants, onSelect, initialAttributes, layout = 'stacked' }: Props): JSX.Element | null {
  const attributeKeys = useMemo(
    () => (variants.length > 0 ? Object.keys(variants[0].attributes) : []),
    [variants],
  );

  const valuesByKey = useMemo(() => {
    const map: Record<string, string[]> = {};
    attributeKeys.forEach(key => {
      const distinct = new Set<string>();
      variants.forEach(v => { if (v.attributes[key]) distinct.add(v.attributes[key]); });
      map[key] = [...distinct];
    });
    return map;
  }, [attributeKeys, variants]);

  const [selections, setSelections] = useState<Record<string, string>>(initialAttributes ?? {});

  // A single, attribute-less variant needs no picker at all — select it immediately.
  useEffect(() => {
    if (attributeKeys.length === 0 && variants.length === 1) {
      onSelect(variants[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variants]);

  const resolvedVariant = useMemo(() => {
    if (attributeKeys.some(key => !selections[key])) return undefined; // selection incomplete
    return variants.find(v => attributeKeys.every(key => v.attributes[key] === selections[key]));
  }, [attributeKeys, selections, variants]);

  useEffect(() => {
    onSelect(resolvedVariant ?? null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resolvedVariant]);

  if (attributeKeys.length === 0) {
    return null; // nothing to pick — the effect above already selected the sole variant
  }

  const selectionComplete = attributeKeys.every(key => selections[key]);

  const chipsFor = (key: string) => valuesByKey[key].map(value => (
    <Chip
      key={value}
      label={value}
      clickable
      size="small"
      color={selections[key] === value ? 'primary' : 'default'}
      variant={selections[key] === value ? 'filled' : 'outlined'}
      onClick={() => setSelections(prev => ({ ...prev, [key]: value }))}
      sx={{ borderRadius: 1 }}
    />
  ));

  return (
    <Stack spacing={layout === 'row' ? 1.5 : 2}>
      {attributeKeys.map(key => (
        layout === 'row' ? (
          <Stack key={key} direction="row" alignItems="center" spacing={2}>
            <Typography
              variant="body1"
              color="text.secondary"
              fontWeight={600}
              sx={{ minWidth: 72, flexShrink: 0, textTransform: 'capitalize' }}
            >
              {key}
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {chipsFor(key)}
            </Box>
          </Stack>
        ) : (
          <Box key={key}>
            <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ textTransform: 'capitalize' }}>
              {key}
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 0.5 }}>
              {chipsFor(key)}
            </Box>
          </Box>
        )
      ))}
      {selectionComplete && !resolvedVariant && (
        <Typography variant="body1" color="error">
          Not available in this combination.
        </Typography>
      )}
    </Stack>
  );
}
