const slots = ['hero', 'pick', 'rebuy', 'consume']
const selectors = ['.travel-hero', '.brand-zone', '.rebuy-zone', '.consume-zone']
const groups = [
  ['.travel-hero-title', '.travel-hero-sub'],
  ['.zone-eyebrow', '.home-zone-title', '.home-zone-caption'],
  ['.home-zone-title', '.home-zone-caption'],
  ['.home-zone-title', '.home-zone-caption'],
]
const fonts = {
  song: '"Songti SC","Noto Serif SC",serif',
  hei: '"PingFang SC","Microsoft YaHei",sans-serif',
  kai: '"STKaiti","KaiTi",serif',
  classic: 'Georgia,"Songti SC",serif',
}
const effects = {
  shadow: '0 2px 8px #172d3b99',
  glow: '0 0 8px #fff, 0 2px 14px #fff9',
  outline: '1px 1px 0 #24372f,-1px -1px 0 #24372f,1px -1px 0 #24372f,-1px 1px 0 #24372f',
  none: 'none',
}
const processed = new WeakSet()
function applyStyle(element, caption) {
  Object.assign(element.style, {
    position: 'absolute', left: caption.x + '%', top: caption.y + '%',
    maxWidth: (100 - caption.x) + '%', margin: '0', color: caption.color,
    fontSize: caption.size + 'px', fontWeight: caption.bold ? '700' : '400',
    fontFamily: fonts[caption.font] || fonts.song,
    letterSpacing: (caption.spacing ?? 0) + 'px',
    textShadow: effects[caption.effect] || effects.shadow,
    lineHeight: '1.2', overflowWrap: 'anywhere',
    display: caption.visible ? 'block' : 'none',
  })
}
function refresh() {
  slots.forEach(async (slot, index) => {
    const card = document.querySelector('.zone-home ' + selectors[index])
    if (!card || processed.has(card)) return
    processed.add(card)
    try {
      const response = await fetch('/static/festive-home/' + slot + '-captions.json', { cache: 'no-store' })
      if (!response.ok) return
      const rows = await response.json()
      if (!Array.isArray(rows) || rows.length !== groups[index].length) return
      card.style.position = 'relative'
      groups[index].forEach((selector, i) => {
        const element = card.querySelector(selector)
        if (element) { element.textContent = rows[i].text; applyStyle(element, rows[i]) }
      })
    } catch { /* Keep the original image when no caption configuration is available. */ }
  })
}
new MutationObserver(refresh).observe(document.body, { subtree: true, childList: true })
refresh()
