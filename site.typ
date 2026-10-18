#context if target() == "bundle" {
  document("index.html", title: [backbone | snlx.net])[
    #include "doc.typ"
  ]
  document("doc.pdf", title: [Backbone])[
    #include "doc.typ"
  ]
  document("backbone.pdf", title: [Backbone])[
    #include "doc.typ"
  ]

  asset("banner.svg", read("banner.svg"))
  asset("app.html", read("app.html"))
}
