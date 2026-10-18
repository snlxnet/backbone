#import "@preview/cades:0.3.1": qr-code

// colors
#let light = rgb("#c9c1ba")
#let dark = rgb("#ad9f92")
#let darker = rgb("#5a4741")
#let darkest = rgb("#3e312a")
#let black = rgb("#000")

// doc
#import "main.typ": theme
#show: theme("/backbone", "/ru/backbone", "banner.svg", "paleo", 07, spikes: true, segment: 8)
#set raw(theme: "min.tmTheme")
#show raw: set text(11pt)

#set text(lang: "en")
#show heading: it => {
  show regex("[A-Za-z]+('[A-Za-z]+)?"): word => upper(word.text.first()) + lower(word.text.slice(1))
  it 
}

The app is currently in early development, here's #link("https://github.com/snlxnet/backbone/releases/download/v0.1.0/backbone.apk")[the .apk]

= The problem
Let's say you need a good camera and a screen for a robot you're making.
The traditional way is a raspberry pi (around 70 USD) with a CSI camera (\$25 for v3)
and an official display (\$40 at least).

The scrappy way is to use a phone. If you don't have an old phone,
you can find one on the used marked for less than just a pi.

But phones
1. Run android, which is not really designed for embedded applications
2. Make it increasingly difficult to develop for with every generation
3. Come with non-unlockable bootloaders now

= What backbone is
It's an app that you can install on any Android 5+ device,
allow it access to the hardware, set it as the launcher and forget about.

Instead of developing for Android, you get this JS API:

```ts
backbone.startup(
    url,          // Link to your actual webapp.
                  // Default: localhost:2903 || backbone.snlx.net/app
    fullscreen,   // Default: false
    shellCommand, // command runs in termux at startup. Default: ""
    delay,        // between shell and url. Defuault: 0
)
backbone.central(deviceNames: string[]) // run as BLE central
backbone.peripheral(deviceName: string) // run as BLE peripheral
backbone.send(message: string, device?: string) // send a message over BLE
backbone.onmessage: (message: string, device: string) => void
```

Peripherals can only `send` to central, so the device is optional for them.
