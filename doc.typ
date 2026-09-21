#import "@preview/cades:0.3.1": qr-code

// colors
#let light = rgb("#c9c1ba")
#let dark = rgb("#ad9f92")
#let darker = rgb("#5a4741")
#let darkest = rgb("#3e312aff")
#let black = rgb("#000")

// doc
#set page(paper: "a4", margin: 1cm, fill: darkest)
#set text(fill: dark, font: "JetBrainsMono NF", weight: "bold", size: 12pt)
#show heading: set text(fill: light)
#set raw(theme: "min.tmTheme")
#show raw.where(block: true): it => block(
  fill: darker,
  inset: 5mm,
  radius: 3mm,
  it
)

#set text(lang: "en")
#show heading: it => {
  show regex("[A-Za-z]+('[A-Za-z]+)?"): word => upper(word.text.first()) + lower(word.text.slice(1))
  it 
}

#box(
  width: 100%,
  outset: (x: 2cm, y: 1cm),
  fill: darker,
  grid(
    columns: (1fr, 1fr),
    image("banner.svg", width: 100%),
    align(
      horizon + right,
      box(
        inset: 8mm,
        width: 60%,
        fill: light,
        radius: 3mm,
        qr-code("https://backbone.snlx.net", background: light, color: darker),
      )
    )
  ),
)

#align(center, image("zigzag.svg", width: 100% + 2cm))

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

#grid(
  columns: 2,
  gutter: 1cm, 
  ```ts
  backbone.startup(
      url: string,
      fullscreen: boolean,
      shellCommand: string,
      delay: number,
  )
  ```,
  [
  These are the app settings:
  - the shell command runs in termux at startup. Set to `""` to disable
  - the url is the link to your actual js app
  - delay between running termux and loading the url
  ],
)

```ts
backbone.central(deviceNames: string[]) // run as BLE central
backbone.peripheral(deviceName: string) // run as BLE peripheral
backbone.send(message: string, device?: string) // send a message over BLE
backbone.onmessage: (message: string, device: string) => void
```

Perepherals can only `send` to central, so the device is optional for them.
