import socket

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("", 2903))

while True:
    data, addr = s.recvfrom(65535)
    print(addr, data)
