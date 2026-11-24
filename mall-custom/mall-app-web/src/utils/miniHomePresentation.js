
const host="https://zhiyoumei.com.cn",hero=host+"/static/festive-home/hero.jpg";
const defaults={pick:[{text:"自选区",visible:true,color:"#ffffff",size:16,bold:true,x:38,y:18,font:"classic",spacing:8,effect:"glow"}],rebuy:[{text:"复购区",visible:true,color:"#ffffff",size:14,bold:true,x:64,y:20,font:"hei",spacing:1,effect:"none"}],consume:[{text:"消费区",visible:true,color:"#ffffff",size:14,bold:true,x:7,y:20,font:"hei",spacing:1,effect:"shadow"}]};
const fonts={song:'"Songti SC","Noto Serif SC",serif',hei:'"PingFang SC","Microsoft YaHei",sans-serif',kai:'"STKaiti","KaiTi",serif',classic:'Georgia,"Songti SC",serif'};
const shadows={none:'none',shadow:'0 2px 8px #172d3b99',glow:'0 0 8px #fff,0 2px 14px #ffffff99',outline:'1px 1px 0 #24372f,-1px -1px 0 #24372f,1px -1px 0 #24372f,-1px 1px 0 #24372f'};
function number(value,fallback,min,max){const n=Number(value);return Number.isFinite(n)?Math.min(max,Math.max(min,n)):fallback;}
function captions(rows,fallback){if(!Array.isArray(rows))rows=fallback;return rows.filter(r=>r&&r.visible!==false&&typeof r.text==='string'&&r.text.trim()).slice(0,8).map(r=>{
const x=number(r.x,5,0,95),y=number(r.y,20,0,95),color=/^#[0-9a-f]{3,8}$/i.test(r.color||'')?r.color:'#ffffff';
return {text:r.text.slice(0,200),style:'left:'+x+'%;top:'+y+'%;max-width:'+(100-x)+'%;color:'+color+';font-size:'+number(r.size,14,8,80)+'px;font-weight:'+(r.bold?'700':'400')+';font-family:'+(fonts[r.font]||fonts.song)+';letter-spacing:'+number(r.spacing,0,0,20)+'px;text-shadow:'+(shadows[r.effect]||shadows.shadow)+';'};});}
function fallbackCaptions(){const out={};Object.keys(defaults).forEach(slot=>{out[slot]=captions(defaults[slot],defaults[slot]);});return out;}
// Do not prepend the host to an already absolute URL.
function absolute(path){return path.startsWith(host+'/')?path:host+'/'+path.replace(/^\//,'');}
function get(path){return new Promise(resolve=>{uni.request({url:absolute(path)+'?t='+Date.now(),method:'GET',timeout:8000,success:r=>{if(r.statusCode!==200)return resolve(null);let data=r.data;try{if(typeof data==='string')data=JSON.parse(data);}catch(e){return resolve(null);}const headers=r.header||{},key=Object.keys(headers).find(k=>k.toLowerCase()==='etag')||Object.keys(headers).find(k=>k.toLowerCase()==='last-modified');resolve({data,version:key?String(headers[key]):''});},fail:()=>resolve(null)});});}
function slides(data,version){if(!Array.isArray(data))return null;const rows=data.filter(p=>typeof p==='string'&&/^\/static\/festive-home\/hero(?:-[a-z0-9-]+)?\.jpg$/.test(p));return Array.from(new Set(rows)).map(p=>absolute(p)+(version?'?v='+encodeURIComponent(version):''));}
let cached,loadedAt=0,lastPresentation;
function load(force){if(!force&&cached&&Date.now()-loadedAt<30000)return cached;loadedAt=Date.now();cached=Promise.all([get('/static/festive-home/hero-carousel.json'),get('/static/festive-home/pick-captions.json'),get('/static/festive-home/rebuy-captions.json'),get('/static/festive-home/consume-captions.json')]).then(rows=>{
const images=slides(rows[0]?.data,rows[0]?.version)||lastPresentation?.images||[hero];
const result={images,captions:{}};
['pick','rebuy','consume'].forEach((slot,index)=>{result.captions[slot]=rows[index+1]?captions(rows[index+1].data,defaults[slot]):lastPresentation?.captions[slot]||captions(defaults[slot],defaults[slot]);});
lastPresentation=result;return result;});return cached;}
function layout(){let width=375;try{const info=typeof wx.getWindowInfo==='function'?wx.getWindowInfo():wx.getSystemInfoSync();width=Number(info.windowWidth)||width;}catch(e){}const wide=width>=900,card=Math.max(200,Math.min(width,1200)-(wide?82:50)),gap=wide?16:9;return {pickHeight:Math.round(card/2),rowWidth:Math.floor((card-gap)/2),rowHeight:Math.round((card-gap)/2/1.84),gap};}
export { fallbackCaptions, load, layout };
