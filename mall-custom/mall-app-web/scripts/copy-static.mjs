import { cpSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const source = fileURLToPath(new URL('../src/static/', import.meta.url))
const destination = fileURLToPath(new URL('../dist/build/h5/static/', import.meta.url))

if (!existsSync(source)) {
  throw new Error(`Missing static assets: ${source}`)
}

cpSync(source, destination, { recursive: true })
console.log('Copied H5 static assets')
