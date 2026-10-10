import socket


def command(sock, *parts):
    data = f"*{len(parts)}\r\n".encode()
    for part in parts:
        raw = part.encode()
        data += f"${len(raw)}\r\n".encode() + raw + b"\r\n"
    sock.sendall(data)
    answer = sock.recv(4096)
    print(parts[0], answer.decode(errors="replace").strip())
    if answer.startswith(b"-"):
        raise RuntimeError(answer)


with socket.create_connection(("127.0.0.1", 6379), timeout=2) as conn:
    command(conn, "PING")
    command(conn, "SET", "phase3a:probe", "ok")
    command(conn, "GET", "phase3a:probe")
    command(conn, "DEL", "phase3a:probe")
