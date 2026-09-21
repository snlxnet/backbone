# The new API
```ts
backbone.startup(props: {
    url: string,
    fullscreen?: boolean,
    shellCommand?: string,
    delay?: number,
})
```

The url opens at startup. The shell command runs in termux. Delay sets the time in milliseconds between the termux is called and the webview is started. This is useful if you set the URL to a local port of a server running in termux.

```ts
backbone.central(deviceNames: string[])
```

```ts
backbone.peripheral(deviceName: string)
```

```ts
backbone.send(message: string, device?: string)
```

Perepherals can only send to central, so the device is optional for them.

```ts
backbone.onmessage: (message: string, device: string) => void
```

`addEventListener` is not implemented, so here's how you can subscribe to incoming messages. The `device` is only set if `ble` is running in `central` mode.

