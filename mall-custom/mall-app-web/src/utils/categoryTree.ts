import type { CategoryTreeNode } from '@/types/product'
export function flattenCategoryTree(nodes: CategoryTreeNode[], depth = 0): (CategoryTreeNode & { depth: number })[] {
  return nodes.flatMap(node => [{ ...node, depth }, ...flattenCategoryTree(node.children || [], depth + 1)])
}
