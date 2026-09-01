import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';

export interface ImageThumbnailItem<TId extends number | string = number | string> {
  id: TId;
  url: string;
  alt: string;
}

interface Props<TId extends number | string> {
  items: ImageThumbnailItem<TId>[];
  /** The id of an item with an in-flight mutation — dims that one thumbnail and disables every
   * item's move/remove controls (a move/remove elsewhere could race the in-flight one against the
   * same underlying sort-order range). `ProductImageStager`'s own add/remove/reorder are
   * synchronous local-array edits with nothing to be in flight, so it never passes this. */
  busyId?: TId | null;
  onMove: (id: TId, direction: -1 | 1) => void;
  onRemove: (id: TId) => void;
  emptyMessage: string;
}

/**
 * The thumbnail grid (image + move-earlier/move-later/remove icon row) shared by
 * `ProductImageGallery` (edit mode — every action is a real backend call, ids are `number`) and
 * `ProductImageStager` (create mode — actions are local-array edits, no network call yet, ids are
 * client-generated `string`s). Generic over the id type rather than a fixed `number | string`
 * union so each caller's `onMove`/`onRemove` stays exactly as narrowly typed as it already was —
 * a fixed union would make a `string`-only callback (`ProductImageStager`'s) fail
 * `strictFunctionTypes` when passed where a `number | string` callback is expected. Extracted once
 * the two components had grown near-byte-identical layout with only the data source and action
 * wiring differing — see each component's own doc comment for why those two stay separate rather
 * than being merged outright.
 */
export default function ImageThumbnailGrid<TId extends number | string>(
  { items, busyId = null, onMove, onRemove, emptyMessage }: Props<TId>,
): JSX.Element {
  if (items.length === 0) {
    return <Typography variant="body2" color="text.secondary">{emptyMessage}</Typography>;
  }

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
      {items.map((item, index) => (
        <Box key={item.id} sx={{ width: 140 }}>
          <Box
            component="img"
            src={item.url}
            alt={item.alt}
            sx={{
              width: 140, height: 140, objectFit: 'cover', borderRadius: 1,
              border: '1px solid', borderColor: 'divider',
              opacity: busyId === item.id ? 0.5 : 1,
            }}
          />
          <Stack direction="row" justifyContent="center" spacing={0.5} sx={{ mt: 0.5 }}>
            <Tooltip title="Move earlier">
              <span>
                <IconButton
                  size="small"
                  disabled={index === 0 || busyId !== null}
                  onClick={() => onMove(item.id, -1)}
                >
                  <ArrowUpwardIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Move later">
              <span>
                <IconButton
                  size="small"
                  disabled={index === items.length - 1 || busyId !== null}
                  onClick={() => onMove(item.id, 1)}
                >
                  <ArrowDownwardIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Remove">
              <span>
                <IconButton size="small" color="error" disabled={busyId !== null} onClick={() => onRemove(item.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        </Box>
      ))}
    </Box>
  );
}
