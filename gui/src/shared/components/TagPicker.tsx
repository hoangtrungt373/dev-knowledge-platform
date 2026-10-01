import { Box, Chip, IconButton, InputAdornment, TextField, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { PickableTag, StagedTagPickerResult } from '@shared/hooks/useStagedTagPicker';

interface TagPickerProps<T extends PickableTag> {
  /** The state from `useStagedTagPicker` — this component only renders it. */
  picker: StagedTagPickerResult<T>;
  /** The caption under the "New tags" queue, e.g. "Created when you save the product." */
  stagedHint: string;
}

/**
 * The body of a form's "Tags" section — an "Existing tags" Chip-toggle-cloud and a separate
 * "New tags" queue for names typed here but not yet created (nothing is persisted to the tag
 * catalog until the owning record is saved — see `useStagedTagPicker`'s `resolveStagedTagIds`).
 * Add-only: renaming/deleting a real tag still happens on that feature's own tag admin page.
 *
 * Purely presentational: state comes in via `picker`, so the owning page keeps control of *when*
 * staged tags are created (inside its own submit). Renders no panel/heading of its own — each page
 * wraps it in its own section panel. Used by `@ecommerce`'s ProductFormPage and `@dev-practice`'s
 * ProblemFormPage.
 */
export default function TagPicker<T extends PickableTag>({ picker, stagedHint }: TagPickerProps<T>): JSX.Element {
  const {
    allTags, selectedTagIds, toggleTag,
    newTagInput, setNewTagInput, handleAddStagedTag,
    stagedTagNames, handleRemoveStagedTag,
  } = picker;

  return (
    <>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
        Existing tags
      </Typography>
      {allTags.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>No tags yet</Typography>
      ) : (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mb: 2 }}>
          {allTags.map(tag => {
            const selected = selectedTagIds.has(tag.id);
            return (
              <Chip
                key={tag.id}
                label={tag.name}
                size="small"
                color={selected ? 'primary' : 'default'}
                variant={selected ? 'filled' : 'outlined'}
                onClick={() => toggleTag(tag.id)}
                clickable
              />
            );
          })}
        </Box>
      )}

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
        New tags
      </Typography>
      <TextField
        placeholder="New tag name…"
        value={newTagInput}
        onChange={e => setNewTagInput(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleAddStagedTag(); } }}
        size="small"
        fullWidth
        inputProps={{ maxLength: 100 }}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                size="small"
                onClick={handleAddStagedTag}
                disabled={!newTagInput.trim()}
                title="Queue tag"
              >
                <AddIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          ),
        }}
        sx={{ mb: 1 }}
      />
      {stagedTagNames.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No new tags queued</Typography>
      ) : (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
          {stagedTagNames.map(tagName => (
            <Chip
              key={tagName}
              label={tagName}
              size="small"
              color="warning"
              variant="outlined"
              onDelete={() => handleRemoveStagedTag(tagName)}
            />
          ))}
        </Box>
      )}
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        {stagedHint}
      </Typography>
    </>
  );
}
