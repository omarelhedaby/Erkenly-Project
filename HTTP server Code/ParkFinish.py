import sys
import RPi.GPIO as GPIO
import time
echo=5
tri=7
GPIO.setmode(GPIO.BOARD)
GPIO.setup(echo,GPIO.IN) #Echo
GPIO.setup(tri,GPIO.OUT) #Tri
GPIO.setup(12,GPIO.OUT)
pwm=GPIO.PWM(12,50)
spotnumber=sys.argv[1]
operation=sys.argv[2]
if(operation=="open"):
    pwm.start(0)
else:
    pwm.start(90)

def Ultrasonic(echo,tri,samples):
    takes=[]
    for i in range(0,samples):
        GPIO.output(tri,False)
        time.sleep(1e-6) #sleep 2 microseconds
        GPIO.output(tri,True)
        time.sleep(10e-6) #sleep 10 microseconds
        GPIO.output(tri,False)
        start1=time.time()
        while(1):
        
            if(GPIO.input(echo)==1):
                break           #wait until echo is high
        start=time.time(); #start timer once its high
        while(1):
            if(GPIO.input(echo)==0):
                break           #wait until echo is low again
        end=time.time()
        timetaken=(end-start)/2
        distance=(343*timetaken)*100;  #distance in centimeters
        if distance<110:
            takes.append(distance)
        time.sleep(0.035)
    if len(takes)==0:
        return 100
    else:
        return sum(takes)/len(takes)



def servoWrite(direction,pwm):
    duty = 10 / 180 * direction + 2
    pwm.ChangeDutyCycle(duty)
    print ("direction =", direction, "-> duty =", duty)
    time.sleep(0.5) # allow to settle
#def setDirection(direction):
   
    
spotnumber=sys.argv[1]
operation=sys.argv[2]

if operation=="open":
    print("open")
    servoWrite(100,pwm)
    while Ultrasonic(echo,tri,15)>12:
        print(Ultrasonic(echo,tri,10))
        continue
    print("closed")
    servoWrite(0,pwm)
else:
    servoWrite(100,pwm)
    print("open")
    while(Ultrasonic(echo,tri,15)<30):
        continue
    print("closed")
    servoWrite(0,pwm)