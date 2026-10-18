#import "colors.typ"
#import "squiggles.typ": squiggles
#import "qr.typ": qr

#let base-css = ```css
@import url('https://fonts.googleapis.com/css2?family=Comfortaa:wght@300..700&family=JetBrains+Mono:ital,wght@0,100..800;1,100..800&display=swap');
body {
  padding: 0 1em;
  padding-bottom: 8em;
  margin: 0;
  padding: 0;
  min-height: calc(100svh - 8em);
  accent-color: var(--accent);
}
main, header, footer {
  max-width: 90ch;
  margin: auto;
  padding: 1em;
}
.squiggles {
  display: flex;
  justify-content: center;
  overflow: hidden;
  height: 5em;
}
.squiggles:last-of-type {
  align-items: flex-end;
}

html, body {
  background: var(--page);
  color: var(--text);
  font-size: 16px;
  font-family: "JetBrains Mono";
  scrollbar-color: var(--text) transparent;
}
header svg {
  width: 100% !important;
  height: unset !important;
}
header, footer {
  background: var(--header);
  box-shadow: 0 0 0 100vmax var(--header);
  clip-path: inset(0 -100vmax);
}
footer {
  text-align: center;
}
h1, h2, h3, h4, a, strong {
  color: var(--accent);
}
pre {
  overflow-x: auto;
  padding: 1em;
  background: var(--header);
  border-radius: 1em;
}
```

#let theme(
  path,
  card,
  colorscheme-name,
  seed,
  ..args,
) = (doc) => context {
  let colorscheme = if target() == "html" {
    colors.screen.at(colorscheme-name)
  } else {
    (: ..colors.screen, ..colors.print).at(colorscheme-name)
  }

  let header = box(
    fill: colorscheme.header,
    width: 100%,
    outset: 10mm,
    grid(
      columns: (1fr, auto),
      box(image(card, height: 6.2cm)),
      [#set text(size: 10pt)
      #qr(path, background: colorscheme.qrbg, color: colorscheme.qrfg)],
    )
  )
  let footer(it) = if target() == "html" {
    html.footer(it)
  } else {
    box(fill: colorscheme.header, width: 100%, outset: 10mm, it)
  }

  set text(font: "JetBrains Mono", fill: colorscheme.text)
  show heading: set text(fill: colorscheme.deep)
  show strong: set text(fill: colorscheme.accent)
  show raw.where(block: true): it => block(inset: 1em, fill: colorscheme.header, radius: 3mm, width: 100%, it)
  set page(margin: 10mm, fill: colorscheme.page)
  
  if target() == "html" {
    html.style(base-css.text)
    html.style(colors.css.at(colorscheme-name))
    html.header(html.frame(box(width: 180mm, header)))
  } else {
    header
  }
  squiggles(
    seed,
    fill: colorscheme.header,
    stroke: 0.3mm + colorscheme.border,
    up: 3,
    ..args,
  )
  show link: underline
  show link: set text(fill: colorscheme.accent)
  if target() == "html" {
    html.main(doc)
  } else {
    doc
  }
  set align(bottom + center)
  squiggles(
    seed + 1,
    fill: colorscheme.header,
    stroke: 0.3mm + colorscheme.border,
    down: -10,
    ..args,
  )
  footer[
    \@snlxnet
    | #link("https://snlx.net")[home]
    | #link("https://github.com/snlxnet")[github]
    | #link(path + ".pdf")[pdf]
  ]
}

#show: theme("/site", "card.svg", "dark", 0)

= Heading

Was it worh making this typst thing?

Next question

#lorem(2000)
