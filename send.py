import socket

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
# s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
s.sendto(input("> ").encode(), ("192.168.50.103", 2903))
