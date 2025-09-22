from socket import *
import threading

def readInput():
    while True:
        sentence = input("")
        sentence = sentence + "\n"
        if not sentence: break
        clientSocket.send(sentence.encode())

def readOutput():
    while True:
        out = clientSocket.recv(1024)
        print(out.decode())

serverName = 'localhost'
serverPort = 2000
# AF_INET indicates the network uses IPv4
# SOCK_STREAM indicates it is a TCP socket
clientSocket = socket(AF_INET, SOCK_STREAM)
# initiates a connection between server and client
clientSocket.connect((serverName, serverPort))
sentence = input("Enter a name: ")
sentence = sentence + "\n"
clientSocket.send(sentence.encode())
o = clientSocket.recv(1024)
while (o.decode().strip() != "Connected..."):
    print(o.decode())
    sentence = input("Enter a name: ")
    sentence = sentence + "\n"
    clientSocket.send(sentence.encode())
    o = clientSocket.recv(1024)
print(o.decode())

inp = threading.Thread(target=readInput)
outp = threading.Thread(target=readOutput)
inp.start()
outp.start()

inp.join()
outp.join()
clientSocket.close()