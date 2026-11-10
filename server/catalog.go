package main

// Cosmetic is one item players can own and equip.
type Cosmetic struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Type   string `json:"type"` // "cape" or "trail"
	Price  int    `json:"price"`
	Rarity string `json:"rarity"`
}

// catalog is the full list of cosmetics. The client ships the matching textures and particle effects.
var catalog = []Cosmetic{
	{ID: "cape_annoying", Name: "Annoying Cape", Type: "cape", Price: 0, Rarity: "Common"},
	{ID: "cape_midnight", Name: "Midnight Stars", Type: "cape", Price: 150, Rarity: "Rare"},
	{ID: "cape_crimson", Name: "Crimson Flame", Type: "cape", Price: 250, Rarity: "Rare"},
	{ID: "cape_emerald", Name: "Emerald Wave", Type: "cape", Price: 250, Rarity: "Rare"},
	{ID: "cape_mono", Name: "Monochrome", Type: "cape", Price: 100, Rarity: "Common"},
	{ID: "cape_galaxy", Name: "Galaxy", Type: "cape", Price: 600, Rarity: "Epic"},
	{ID: "cape_admin", Name: "Staff Cape", Type: "cape", Price: -1, Rarity: "Exclusive"}, // admins only (granted, never sold)
	{ID: "trail_hearts", Name: "Heart Trail", Type: "trail", Price: 200, Rarity: "Rare"},
	{ID: "trail_flames", Name: "Flame Trail", Type: "trail", Price: 300, Rarity: "Rare"},
	{ID: "trail_sparkle", Name: "Sparkle Trail", Type: "trail", Price: 400, Rarity: "Epic"},
	{ID: "trail_notes", Name: "Music Trail", Type: "trail", Price: 150, Rarity: "Common"},
}

func findCosmetic(id string) *Cosmetic {
	for i := range catalog {
		if catalog[i].ID == id {
			return &catalog[i]
		}
	}
	return nil
}
