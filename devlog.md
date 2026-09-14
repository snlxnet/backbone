---
tags:
  - project
up: "[[project-dolphin]]"
updated: 2026-09-14T12:01:00+03:00
created: 2026-09-11T08:12:43+03:00
---
What I want:
- a microservice for finding the distance between plates, obstacles and stuff
- a client for android to use for the sensor data
- a backbone for integrating multiple android boxes, linux boxes and MCUs, with autostart for each

---

MVP:
- 2 phones and 1 mac/linux
- HTTP GET `localhost:17500/[port]` for opening a ws port on all connected devices
- HTTP GET `localhost:17500/` for getting a json array of all open ports
- ws forwarding of any open port
- ws `localhost:2040` is the rp2040 repl (the one connected via usb)
- MCU support: rp2040, rp2350 (untested)
- flashes itself

- `:2040/reset` resets the link & the repl