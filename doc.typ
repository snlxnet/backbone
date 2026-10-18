#import "@preview/cades:0.3.1": qr-code

// colors
#let light = rgb("#c9c1ba")
#let dark = rgb("#ad9f92")
#let darker = rgb("#5a4741")
#let darkest = rgb("#3e312a")
#let black = rgb("#000")

// doc
#import "main.typ": theme
#show: theme("/backbone", "banner.svg", "paleo", 09, spikes: true, segment: 6)
#set raw(theme: "min.tmTheme")

#html.style(```css
.side-by-side {
  display: flex;
  gap: 1em;
  overflow-x: auto;
}
```.text)

#context if target() == "html" {
  html.link(rel: "stylesheet", href: "doc.css")
}

#set text(lang: "en")
#show heading: it => {
  show regex("[A-Za-z]+('[A-Za-z]+)?"): word => upper(word.text.first()) + lower(word.text.slice(1))
  it 
}

#let doc(it) = context if target() == "html" { html.main(it) } else { it }
#let side-by-side(a, b) = context if target() == "html" {
  html.div(class: "side-by-side")[
    #html.div(a)
    #html.div(b)
  ]
} else {
  grid(
    columns: 2,
    gutter: 1cm, 
    a,
    b,
  )
}

#doc[
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

#side-by-side(
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
  - `shell`: command runs in termux at startup.\ Default: `""`
  - `url`: link to your actual js app.\ Default: `localhost:2903 || backbone.snlx.net/app`
  - `delay` between `shell` and `url`.\ Defuault: `0`
  ],
)

```ts
backbone.central(deviceNames: string[]) // run as BLE central
backbone.peripheral(deviceName: string) // run as BLE peripheral
backbone.send(message: string, device?: string) // send a message over BLE
backbone.onmessage: (message: string, device: string) => void
```

Peripherals can only `send` to central, so the device is optional for them.

#context if target() == "html" [
    This doc also has a #link("/doc.pdf")[print version].
]
]
