import { IconButton } from '@mui/material';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import TaskRow, { TaskRowProps } from './TaskRow';

// Wraps TaskRow with a drag handle for views that support manual reordering (TasksPage's
// project-filter and Today/This-week flat lists — see useTaskOrder). Kept as a separate component
// rather than baking @dnd-kit directly into TaskRow so the far more common non-draggable uses of
// TaskRow (subtasks inside TaskDetailPanel, which aren't independently orderable) don't carry a
// dnd-kit dependency at all.
export default function SortableTaskRow(props: Omit<TaskRowProps, 'dragHandle'>): JSX.Element {
  const { task } = props;
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: task.id });

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition: transition ?? undefined,
        opacity: isDragging ? 0.5 : 1,
      }}
    >
      <TaskRow
        {...props}
        dragHandle={
          <IconButton
            size="small"
            className="task-row-drag-handle"
            // A plain click (no drag) still bubbles up as a click event distinct from the
            // pointer-down @dnd-kit listens for below — stop it here so grabbing the handle
            // doesn't also fire the row's onSelect/startEditingTitle, same guard every other
            // directly-clickable child of this row already has.
            onClick={e => e.stopPropagation()}
            {...attributes}
            {...listeners}
            sx={{ cursor: 'grab', opacity: 0, transition: 'opacity 0.1s', touchAction: 'none' }}
          >
            <DragIndicatorIcon fontSize="small" />
          </IconButton>
        }
      />
    </div>
  );
}
