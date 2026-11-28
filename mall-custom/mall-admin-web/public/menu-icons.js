const labels=new Set(['消费区商品管理','宝贝详情','栏目图片']);
function icons(){document.querySelectorAll('.el-menu-item,.el-sub-menu__title').forEach(el=>{
const spans=Array.from(el.querySelectorAll('span')).filter(s=>labels.has(s.textContent.trim()));
spans.forEach(s=>{if(el.querySelector('.parity-menu-icon'))return;const i=document.createElement('span');i.className='parity-menu-icon';i.style.cssText='display:inline-flex;width:18px;margin-right:10px;vertical-align:middle';
i.innerHTML='<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="3" width="18" height="18" rx="3"/><path d="M7 8h10M7 12h10M7 16h6"/></svg>';s.before(i);});});}
new MutationObserver(icons).observe(document.documentElement,{childList:true,subtree:true});icons();