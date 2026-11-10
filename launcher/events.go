package main

import (
	"encoding/json"
	"sync"
)

// EventHub fans server-sent events out to every open launcher window.
type EventHub struct {
	mu      sync.Mutex
	clients map[chan []byte]struct{}
	onCount func(int)
}

func newEventHub() *EventHub {
	return &EventHub{clients: map[chan []byte]struct{}{}}
}

func (h *EventHub) Send(kind string, data any) {
	payload, _ := json.Marshal(map[string]any{"type": kind, "data": data})
	h.mu.Lock()
	defer h.mu.Unlock()
	for c := range h.clients {
		select {
		case c <- payload:
		default: // slow client: drop rather than block the game log
		}
	}
}

func (h *EventHub) subscribe() chan []byte {
	c := make(chan []byte, 512)
	h.mu.Lock()
	h.clients[c] = struct{}{}
	n := len(h.clients)
	cb := h.onCount
	h.mu.Unlock()
	if cb != nil {
		cb(n)
	}
	return c
}

func (h *EventHub) unsubscribe(c chan []byte) {
	h.mu.Lock()
	delete(h.clients, c)
	n := len(h.clients)
	cb := h.onCount
	h.mu.Unlock()
	if cb != nil {
		cb(n)
	}
}

func (h *EventHub) notify(level, title, message string) {
	h.Send("toast", map[string]string{"level": level, "title": title, "message": message})
}
