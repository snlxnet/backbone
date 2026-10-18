#import "@preview/cades:0.3.1": qr-code

// colors
#let light = rgb("#c9c1ba")
#let dark = rgb("#ad9f92")
#let darker = rgb("#5a4741")
#let darkest = rgb("#3e312a")
#let black = rgb("#000")

// doc
#import "main.typ": theme
#show: theme("/backbone", "/ru/backbone", "banner.svg", "paleo", 07, spikes: true, segment: 8, ru-footer: true)
#set raw(theme: "min.tmTheme")
#show raw: set text(11pt)

#set text(lang: "ru")

(Черновик перевода был сделан GPT-6 Luna)

Приложение пока находится на ранней стадии разработки. Вот #link("https://github.com/snlxnet/backbone/releases/download/v0.1.0/backbone.apk")[.apk].

= Проблема

Представьте, что вам нужна хорошая камера и экран для робота, которого вы собираете.

Традиционный вариант — Raspberry Pi (минимум 6 000 #sym.ruble), CSI-камера (4 000+ #sym.ruble за версию v3) и официальный дисплей (от 5 300 #sym.ruble).

Вариант для тех, кто любит находить обходные пути --- взять телефон. Если у вас нет старого, на вторичном рынке можно найти телефон дешевле, чем одна Raspberry Pi.

Недостатки телефонов:

1. Android, который не очень подходит для встроенных систем.
2. С каждым поколением разработка под них становится всё сложнее.
3. Современные телефоны часто поставляются с загрузчиками, которые нельзя разблокировать.

= Что такое Backbone

Это приложение, которое можно установить на любое устройство с Android 5 или новее, предоставить ему доступ к аппаратным компонентам, назначить его лаунчером — и забыть о нём.

Вместо разработки под Android вы получаете такой JS API:

```ts
backbone.startup(
    url,          // Ссылка на ваше веб-приложение.
                  // По умолчанию: localhost:2903 || backbone.snlx.net/app
    fullscreen,   // По умолчанию: false
    shellCommand, // Команда, выполняемая в Termux при запуске. По умолчанию: ""
    delay,        // Задержка между запуском команды и открытием URL. По умолчанию: 0
)
backbone.central(deviceNames: string[]) // запуск в роли BLE-центрального устройства
backbone.peripheral(deviceName: string) // запуск в роли BLE-периферийного устройства
backbone.send(message: string, device?: string) // отправка сообщения через BLE
backbone.onmessage: (message: string, device: string) => void
```

Периферийные устройства могут отправлять сообщения только центральному, поэтому параметр `device` для них необязателен.
