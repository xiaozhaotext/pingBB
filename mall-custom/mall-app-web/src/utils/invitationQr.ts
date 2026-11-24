import QRCode from 'qrcode'

/** Older WeChat webviews and mini programs may not expose TextEncoder. */
export function invitationQrData(link: string): QRCode.QRCodeSegment[] {
  const encoded = encodeURIComponent(link)
  const bytes: number[] = []
  for (let i = 0; i < encoded.length; i++) {
    if (encoded[i] === '%') { bytes.push(parseInt(encoded.slice(i + 1, i + 3), 16)); i += 2 }
    else bytes.push(encoded.charCodeAt(i))
  }
  return [{ data: new Uint8Array(bytes), mode: 'byte' }]
}

/** Only QR encoding runs in JS; drawing uses the canvas API available on both H5 and WeChat. */
export function drawInvitationQr(link: string, canvasId: string, scope?: any): Promise<void> {
  const { modules } = QRCode.create(invitationQrData(link), { errorCorrectionLevel: 'M' })
  const size = 240
  const cell = Math.floor(size / (modules.size + 8))
  const offset = Math.floor((size - modules.size * cell) / 2)
  const context = uni.createCanvasContext(canvasId, scope)
  context.setFillStyle('#ffffff')
  context.fillRect(0, 0, size, size)
  context.setFillStyle('#000000')
  for (let row = 0; row < modules.size; row++) {
    let start = -1
    for (let column = 0; column <= modules.size; column++) {
      const dark = column < modules.size && modules.get(row, column)
      if (dark && start < 0) start = column
      if (!dark && start >= 0) {
        context.fillRect(offset + start * cell, offset + row * cell, (column - start) * cell, cell)
        start = -1
      }
    }
  }
  return new Promise(resolve => context.draw(false, resolve))
}
