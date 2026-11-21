"use strict";
const blocked=["pages/member/walletWithdraw","pages/member/earnings"];
let returning=false;
function isBlocked(url){try{return blocked.indexOf(decodeURIComponent(String(url||"").split("?")[0]).replace(/^\//,""))>=0;}catch(e){return true;}}
function home(){if(returning)return;returning=true;wx.switchTab({url:"/pages/index/index",fail:function(){wx.reLaunch({url:"/pages/index/index"});},complete:function(){setTimeout(function(){returning=false;},200);}});}
function install(uni){["navigateTo","redirectTo","reLaunch","switchTab"].forEach(function(method){uni.addInterceptor(method,{invoke:function(args){if(isBlocked(args.url)){home();return false;}}});});}
exports.blocked=blocked;exports.isBlocked=isBlocked;exports.home=home;exports.install=install;
