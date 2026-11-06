import { ref } from 'vue'
// Shared across route unmount/remount so one upload cannot be submitted twice.
export const productImportBusy = ref(false)
