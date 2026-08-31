import { useEffect } from 'react';
import { Box, Divider, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import Underline from '@tiptap/extension-underline';
import FormatBoldIcon from '@mui/icons-material/FormatBold';
import FormatItalicIcon from '@mui/icons-material/FormatItalic';
import FormatStrikethroughIcon from '@mui/icons-material/FormatStrikethrough';
import FormatUnderlinedIcon from '@mui/icons-material/FormatUnderlined';
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered';
import FormatQuoteIcon from '@mui/icons-material/FormatQuote';
import LinkIcon from '@mui/icons-material/Link';
import ImageIcon from '@mui/icons-material/Image';
import UndoIcon from '@mui/icons-material/Undo';
import RedoIcon from '@mui/icons-material/Redo';
import TitleIcon from '@mui/icons-material/Title';

interface Props {
  value: string;
  onChange: (html: string) => void;
}

/**
 * WYSIWYG editor for {@code Product.description} — TipTap-backed, per the accepted "sanitized HTML"
 * plan (Option A). The active node/mark set here is deliberately kept in sync with
 * `ecommerce-service`'s `ProductDescriptionSanitizer` allowlist
 * (`BLOCKS`+`FORMATTING`+`LINKS`+`IMAGES`+`TABLES`) — offering a formatting option the backend would
 * just silently strip on save would be a confusing, "it looked right in the editor" experience for
 * whoever's authoring the description.
 *
 * <p>`StarterKit`'s `horizontalRule` and `codeBlock` nodes are disabled outright rather than exposed
 * with no toolbar button — {@code ProductDescriptionSanitizerTest} confirms `<hr>` is dropped
 * entirely and `<pre><code>` degrades to a bare inline `<code>` (block-level code formatting is
 * lost), and a product description has no real need for either. Everything else `StarterKit`
 * enables by default (paragraph/headings/bold/italic/strike/lists/blockquote/hard break/undo-redo)
 * survives sanitization untouched, confirmed the same way. `Link`/`Image` are added on top —
 * neither is part of `StarterKit`.
 *
 * <p>Uncontrolled-by-design: TipTap owns the editor's live document internally (that's how any rich-
 * text editor works — a plain controlled `value` prop re-rendering the whole DOM on every keystroke
 * fights the editor and loses cursor position). `value` only seeds the editor once, on mount;
 * `onChange` is how the parent form field learns about every edit afterward, the same "read via a
 * callback, don't feed it back in as a prop" shape `ProductVariantEditor`/`ProductImageGallery`
 * already use for their own non-trivial child state.
 *
 * @author ttg
 */
export default function ProductDescriptionEditor({ value, onChange }: Props): JSX.Element {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        horizontalRule: false,
        codeBlock: false,
      }),
      Link.configure({
        openOnClick: false,
        autolink: true,
      }),
      Image,
      Underline,
    ],
    content: value,
    onUpdate: ({ editor: e }) => onChange(e.getHTML()),
  });

  // Edit mode loads the product asynchronously after the editor has already mounted with an empty
  // seed — this syncs the loaded description in once, without fighting the editor on every
  // subsequent keystroke the way a plain controlled `value` prop would (see the class Javadoc).
  useEffect(() => {
    if (editor && value && editor.isEmpty) {
      editor.commands.setContent(value);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor, value]);

  if (!editor) {
    return <Box sx={{ minHeight: 160 }} />;
  }

  const setLink = (): void => {
    const previousUrl = editor.getAttributes('link').href as string | undefined;
    const url = window.prompt('Link URL', previousUrl ?? 'https://');
    if (url === null) return;
    if (url === '') {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };

  const addImage = (): void => {
    const url = window.prompt('Image URL');
    if (!url) return;
    editor.chain().focus().setImage({ src: url }).run();
  };

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>Description</Typography>
      <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
        <Stack
          direction="row"
          alignItems="center"
          spacing={0.5}
          sx={{ px: 1, py: 0.5, borderBottom: '1px solid', borderColor: 'divider', flexWrap: 'wrap' }}
        >
          <Tooltip title="Heading">
            <IconButton
              size="small"
              color={editor.isActive('heading', { level: 2 }) ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
            >
              <TitleIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Bold">
            <IconButton
              size="small"
              color={editor.isActive('bold') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleBold().run()}
            >
              <FormatBoldIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Italic">
            <IconButton
              size="small"
              color={editor.isActive('italic') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleItalic().run()}
            >
              <FormatItalicIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Strikethrough">
            <IconButton
              size="small"
              color={editor.isActive('strike') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleStrike().run()}
            >
              <FormatStrikethroughIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Underline">
            <IconButton
              size="small"
              color={editor.isActive('underline') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleUnderline().run()}
            >
              <FormatUnderlinedIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

          <Tooltip title="Bulleted list">
            <IconButton
              size="small"
              color={editor.isActive('bulletList') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleBulletList().run()}
            >
              <FormatListBulletedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Numbered list">
            <IconButton
              size="small"
              color={editor.isActive('orderedList') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleOrderedList().run()}
            >
              <FormatListNumberedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Quote">
            <IconButton
              size="small"
              color={editor.isActive('blockquote') ? 'primary' : 'default'}
              onClick={() => editor.chain().focus().toggleBlockquote().run()}
            >
              <FormatQuoteIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

          <Tooltip title="Link">
            <IconButton size="small" color={editor.isActive('link') ? 'primary' : 'default'} onClick={setLink}>
              <LinkIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Image">
            <IconButton size="small" onClick={addImage}>
              <ImageIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          <Box sx={{ flexGrow: 1 }} />

          <Tooltip title="Undo">
            <span>
              <IconButton size="small" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()}>
                <UndoIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Redo">
            <span>
              <IconButton size="small" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()}>
                <RedoIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        <Box
          sx={{
            px: 1.5,
            py: 1.25,
            minHeight: 160,
            maxHeight: 400,
            overflowY: 'auto',
            '& .ProseMirror': { outline: 'none' },
            '& p': { m: 0, mb: 1 },
            '& p:last-child': { mb: 0 },
            '& h2, & h3': { mt: 1.5, mb: 1 },
            '& ul, & ol': { pl: 3, mb: 1 },
            '& blockquote': {
              borderLeft: '3px solid', borderColor: 'divider', pl: 1.5, ml: 0, color: 'text.secondary',
            },
            '& img': { maxWidth: '100%', borderRadius: 1 },
          }}
        >
          <EditorContent editor={editor} />
        </Box>
      </Box>
    </Box>
  );
}
