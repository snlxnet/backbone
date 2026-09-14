import sys
import serial

device = sys.argv[1]
print("Connecting to " + device)

with serial.Serial(device, 115200, timeout=1) as ser:
    ser.write(b'print("hi")\r\n')
    print(ser.readlines())
    ser.close()
