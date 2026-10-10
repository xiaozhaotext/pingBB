import logging
from .config import settings

class RedisRuntime:
    def __init__(self):
        self.client=None
        try:
            from redis import Redis
            self.client=Redis.from_url(settings.redis_url,decode_responses=True,socket_connect_timeout=0.2,socket_timeout=0.2)
            self.client.ping()
        except Exception:
            self.client=None
            logging.getLogger("api").warning("redis_unavailable; shared-state operations fail closed")
    def ping(self):
        if self.client is None:return False
        try:return bool(self.client.ping())
        except Exception:return False
    def incr_with_expiry(self,key,window):
        if not self.ping():return None
        pipe=self.client.pipeline();pipe.incr(key);pipe.expire(key,window);return pipe.execute()[0]
    def cache_get(self,key):
        if not self.ping():return None
        import json
        try:
            value=self.client.get("ecom:cache:"+key)
            return json.loads(value) if value is not None else None
        except Exception:return None
    def cache_set(self,key,value,ttl_seconds=300):
        if not self.ping() or ttl_seconds<1:return False
        import json
        try:return bool(self.client.set("ecom:cache:"+key,json.dumps(value,separators=(",",":"),ensure_ascii=False),ex=ttl_seconds))
        except Exception:return False
    def cache_delete(self,key):
        if not self.ping():return False
        try:return bool(self.client.delete("ecom:cache:"+key))
        except Exception:return False
redis_client=RedisRuntime()
_local={}
def rate_limit(key,limit,window):
    if settings.redis_required and not redis_client.ping():return False
    try:
        if redis_client.client:
            count=redis_client.incr_with_expiry("ecom:rl:"+key,window)
            if count is not None:return count<=limit
    except Exception:pass
    # Safe degraded mode: bounded in-process limiter; never silently disable the control.
    import time
    now=time.time();count,start=_local.get(key,(0,now))
    if now-start>window:count,start=0,now
    count+=1;_local[key]=(count,start)
    if len(_local)>10000:
        for k,(_,t) in list(_local.items())[:1000]:
            if now-t>window:_local.pop(k,None)
    return count<=limit
