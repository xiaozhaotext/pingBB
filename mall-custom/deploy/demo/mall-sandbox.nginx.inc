# Isolated membership purchase test site. This must only proxy to the sandbox portal.
location = /sandbox { return 301 /sandbox/; }
location = /sandbox/index.html {
    alias /opt/mall-demo/sandbox/index.html;
    add_header Cache-Control "no-cache, must-revalidate";
}
location ^~ /sandbox/portal/ {
    proxy_pass http://127.0.0.1:8086/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
}
location ^~ /sandbox/ {
    alias /opt/mall-demo/sandbox/;
    try_files $uri $uri/ /sandbox/index.html;
}
