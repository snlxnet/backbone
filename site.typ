#context if target() == "bundle" {
  document("index.html", title: [backbone | snlx.net])[
    #include "doc.typ"
  ]
  document("doc.pdf", title: [Home])[
    #include "doc.typ"
  ]

  asset("doc.css", read("doc.css"))
  asset("banner.svg", read("banner.svg"))
  asset("app.html", read("app.html"))
}
