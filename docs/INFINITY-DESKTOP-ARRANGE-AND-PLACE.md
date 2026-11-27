# Desktop Arrange-and-Place Pack

This pack connects the previously tested arrangement policies with a deterministic mapping from arranged IDs to grid slots. It returns immutable maps and collapses duplicate IDs. It is suitable for a future Compose UI/preferences adapter.

It is not yet wired to gestures or desktop context menu rendering. The current integration PR remains unmerged. Unit tests cover ID order, duplicate IDs, sort order and narrow/invalid viewport handling.
