from firebase_admin import credentials, firestore, messaging
import firebase_admin
import RPi.GPIO as GPIO
import threading
from gpiozero import Button
import time

cred = credentials.Certificate("./ErkenlyService.json")
app = firebase_admin.initialize_app(cred)
db = firestore.client()
buzzer=13
flame=3
GPIO.setmode(GPIO.BOARD)
GPIO.setup(12,GPIO.OUT)
pwm=GPIO.PWM(12,50) #servo2
pwm.start(0)
GPIO.setup(11,GPIO.OUT)
pwm2=GPIO.PWM(11,50) #servo1
pwm2.start(0)
GPIO.setup(buzzer,GPIO.OUT)
GPIO.setup(flame,GPIO.IN,pull_up_down=GPIO.PUD_DOWN)
GPIO.output(buzzer,False)


def Firemessage(token):
    message = messaging.Message(
        data={
            'title': 'Fire Alarm',
            'body': 'There Has been a fire in the place come take your car ASAP!',
        },
        token=token,
    )
    response = messaging.send(message)
    # Response is a message ID string.
    print('Successfully sent message:', response)

    

def servoWrite(direction,pwm):
    duty = 10 / 180 * direction + 2
    pwm.ChangeDutyCycle(duty)
    print ("direction =", direction, "-> duty =", duty)
    time.sleep(0.3)# allow to settle
    
def updateStatus(spotnumber,stat):
    spots = db.collection(u'status').where(u'slot', u'==', spotnumber).stream()
    for spot in spots:
        db.collection(u'status').document(spot.id).update({'status': stat})


def sendMessages(): #fired when interrupt of fire sensor goes High
    #search in the rentings collection and send to their users the message
    print(GPIO.input(3))
    GPIO.output(buzzer,True)
    servoWrite(90,pwm)
    #servoWrite(90,pwm2)
    
    rentings=db.collection(u'rentings').stream()
    uid=[]
    tokens=[]
    for rent in rentings:
        uid.append(rent.to_dict()['uid'])
    for i in uid:
        users=db.collection(u'users').where('uid','==',i).stream()
        for user in users:
            tokens.append(user.to_dict()['token'])
   # users=db.collection(u'users').where(u'uid',u'array_contains_any',uid).stream()
    delete=0
    for token in tokens:
        Firemessage(token)
        delete=1
    if(delete):
        col=db.collection(u'rentings').stream()
        for doc in col:
            db.collection(u'rentings').document(doc.id).delete()
    for i in range(1,3):
        updateStatus(i,-1) # return all spots to out of service once there is a fire


def endAlarm(): #fired when interrupt of fire goes low
    GPIO.output(buzzer,False)
    servoWrite(0,pwm)
    servoWrite(0,pwm2)
    for i in range(1,3):
        updateStatus(i,1) # return all spots to available once fire is out
        

reset=0
reset2=0
while 1:
    flameVal=GPIO.input(flame)
    if(flameVal==0 and reset==0):
        continue
    if(flameVal==1 and reset==0):
        sendMessages()
        reset=1
        reset2=0
    elif (flameVal==0 and reset2==0):
        endAlarm()
        reset=0
        reset2=1
        
        
    
  

  #interrupt rising
#GPIO.add_event_detect(flame, GPIO.FALLING, callback=endAlarm, bouncetime=300)   #interrupt interrupt falling