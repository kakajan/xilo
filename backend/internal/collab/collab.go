package collab

// Phase 5: Collaborative editing via CRDT (Yjs + Tiptap)
// Status: SPEC ONLY — implementation deferred for post-launch
//
// crdt/collab.go - Collaborative editor sync protocol
// - Yjs document provider for Tiptap
// - WebSocket sync transport
// - Awareness protocol (cursors, selections)
// - Conflict resolution via Yjs CRDT merge
