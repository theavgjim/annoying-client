# Annoying Client cosmetics server

Stores coins, owned and equipped cosmetics (capes, trails) and admin roles for Annoying Client.
Standard library Go, one JSON database file, no other services needed.

## How sign-in works
Exactly like a Minecraft server checks players: the client asks Mojang to "join" a random server id
with the player's own session, then this server asks Mojang (`hasJoined`) who joined. Nobody can sign
in as someone else, so admin rights can't be faked.

## Configuration
| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Listen port |
| `DATA` | `data/cosmetics.json` | Database file (keep it on persistent storage) |
| `ADMINS` | `theavgjim_` | Owners (comma-separated usernames). They become admin on their first sign-in and can't be removed by other admins |

New players get 100 coins and the Annoying Cape. Everyone can claim 50 coins every 20 hours.

## Run it
```bash
go run .
```
or with Docker:
```bash
docker build -t annoying-cosmetics .
docker run -d -p 8080:8080 -v annoying-data:/data annoying-cosmetics
```
Put it behind HTTPS (any host with automatic TLS works: Fly.io, Render, Railway, a VPS with Caddy).
The client only connects over `https://`, except to a server on the same computer.

Then in game: **Right Shift > Wardrobe**, paste the server address, **Connect**.

## Admin actions (in the Wardrobe tab for admins)
Give or take coins, give or remove cosmetics, make or remove admins, send an announcement to everyone,
list players. Every action is checked here on the server and written to the audit log in the database.

## Tests
```bash
go test ./...
```
