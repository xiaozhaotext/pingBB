const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

const source = fs.readFileSync(path.join(__dirname, '../src/utils/cartCheckout.ts'), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const moduleValue = { exports: {} }
vm.runInNewContext(compiled, { module: moduleValue, exports: moduleValue.exports, Number, JSON, decodeURIComponent, encodeURIComponent })
const { buildCartCheckoutUrl, parseCartIds } = moduleValue.exports

test('checkout URL round trips both encoded and already decoded page parameters', () => {
  const url = buildCartCheckoutUrl(['12', '34'])
  const raw = url.split('cartIds=')[1]
  assert.deepEqual(Array.from(parseCartIds(raw)), [12, 34])
  assert.deepEqual(Array.from(parseCartIds(decodeURIComponent(raw))), [12, 34])
})

test('checkout rejects malformed or non-cart IDs without throwing during page load', () => {
  for (const raw of ['%ZZ', 'INVALID', '[0]', '["abc"]', '[1,-2]', '[]', undefined]) {
    assert.deepEqual(Array.from(parseCartIds(raw)), [])
  }
})

