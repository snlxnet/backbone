---
tags:
  - project
up: "[[project-dolphin]]"
updated: 2026-09-14T12:30:18+03:00
created: 2026-09-11T08:12:43+03:00
---
MVP:
- 2 phones and 1 mac/linux
- HTTP GET `localhost:17500/[port]` for opening a ws port on all connected devices
- HTTP GET `localhost:17500/` for getting a json array of all open ports
- ws forwarding of any open port
- ws `localhost:2040` is the rp2040 repl (the one connected via usb)
- MCU support: rp2040
- `:2040/reset` resets the link & the repl
- insert an SD card to copy the new server files

roadmap:
- 2026-09-11 brainstorming
- 2026-09-14 design & spec draft
- 2026-09-18 mvp
- 2026-09-22 packaging

# Design

## Choosing the stack
Language. The services that the backbone will connect together are going to be written mostly in Python, and having a REPL on the MCU in the same language as everything else is nice.

(START_POMODORO)

Network. Choosing aiohttp as a single lightweight library that can be an HTTP server, a WS server and a WS client. Frameworks like Flask are unnecessary for the task, this is not a website, it's only going to ever be accessed by localhost or the LAN.

Serial. This is going to be fun because it's not the same across all platforms I want to support. I've started looking at making a native android app, but that means two entirelly separate implementations (one for UNIX, one for android). In theory it should be possible to stick to UNIX ([[android is just weird unix]]) with Termux and Termux:API on the android side. In theory it's just pyserial, then. Need to experiment with it to find out for sure.

## Experiment one. Simple serial
Starting on this computer, this should be easy.
