import http from '@/utils/http'

export async function uploadProductImage(file: File | Blob, name?: string): Promise<string> {
  const form = new FormData()
  form.append('file', file, name || (file instanceof File ? file.name : 'image.png'))
  const response = await http.post<{ url: string }>('/product/image/upload', form)
  return response.data.url
}
