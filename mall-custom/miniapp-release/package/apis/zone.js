"use strict";const t=require("../utils/http.js");exports.getZoneProductsAPI=(e,r={})=>t.http({method:"GET",url:`/product/zone/${e}`,params:r});
