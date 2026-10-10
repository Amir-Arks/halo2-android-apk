# Milestone 4 — index-format reconnaissance

This milestone adds a bounded, exploratory scan over the map's declared index region.

## What it does
- Reads only the index region after checking its offset and length against the selected map file.
- Scans aligned 32-bit words and counts values whose four bytes are printable ASCII.
- Caps the UI output to 24 unique candidate strings.

## What it does not do
Printable four-byte sequences are **not** treated as confirmed tag-group identifiers or decoded tag records. The actual Halo 2 index-entry layout still needs to be established from verified format documentation or the decompiled engine structures before tag parsing is implemented.

## Acceptance check
On the existing `00a_introduction.map`, header and index bounds should still pass. The new diagnostic section should appear below the raw index sample. No asset loading or gameplay is claimed by this milestone.
