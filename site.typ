#let png(..args) = for file in args.pos() { document(file.replace(".svg", ".png"), format: "png")[
  #set page(
    width: 600pt,
    height: 315pt,
    background: image(file, height: 100%),
    fill: rgb("#51403b"),
  )
]}

#context if target() == "bundle" {
  document("index.html", title: [backbone | snlx.net], include "en.typ")
  document("ru.html", title: [backbone | snlx.net], include "ru.typ")
  document("doc.pdf", title: [Backbone], include "en.typ")
  document("backbone.pdf", title: [Backbone], include "en.typ")
  document("ru/backbone.pdf", title: [Backbone], include "ru.typ")

  asset("banner.svg", read("banner.svg"))
  png("banner.svg")
  asset("app.html", read("app.html"))
  asset("favicon.svg", read("favicon.svg"))
}
