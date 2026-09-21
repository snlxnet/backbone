#context if target() == "bundle" {
  document("index.html", title: [Home])[
    #include "doc.typ"
  ]

  asset("doc.css", read("doc.css"))
}
