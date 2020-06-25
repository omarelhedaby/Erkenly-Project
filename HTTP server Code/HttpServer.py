from firebase_admin import credentials,firestore
import firebase_admin
from http.server import BaseHTTPRequestHandler, HTTPServer
import time
import json
from subprocess import call
import threading
from datetime import datetime,timedelta
import RPi.GPIO as GPIO
import time

echo=5
tri=7
GPIO.setmode(GPIO.BOARD)
GPIO.setup(echo,GPIO.IN) #Echo
GPIO.setup(tri,GPIO.OUT) #Tri

priceperhour=15 #EGB
cred = credentials.Certificate("./ErkenlyService.json")
app = firebase_admin.initialize_app(cred)
db = firestore.client()

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
        time.sleep(0.025)
    if len(takes)==0:
        return 100
    else:
        return sum(takes)/len(takes)

def updateStatus(spotnumber,stat):
    spots = db.collection(u'status').where(u'slot', u'==', spotnumber).stream()
    for spot in spots:
        db.collection(u'status').document(spot.id).update({'status': stat})


def Fire():
    call(["python3", "FireAlarm.py"])
    # call(["python3", "FCM.py"])
def FCM():
     call(["python3", "FCM.py"])
    # call(["python3", "FCM.py"])
def Park(spot,op):
    call(["python3", "ParkFinish.py",str(spot),op])
    # call(["python3", "FCM.py",args[0],args[1]])

class MyServer(BaseHTTPRequestHandler):
    # processThread = Process(target=self.another_thread, args=(["hello", '1', '3', '4'],));
    # processThread.start();

    def do_GET(self):
        jsonresponse = {}
        message = (self.rfile.read().decode())
        jsonreply = json.loads(message)
        self.send_response(200)
        self.send_header("Content-type", "text/json")
        self.end_headers()
        self.wfile.write(json.dumps(jsonresponse).encode())
    def do_POST(self):
        path=self.path
        print(path)
        jsonresponse={ }
        length = (self.headers)['Content-Length']
        message = (self.rfile.read(int(length)).decode())
        jsonreply = json.loads(message)
        uid = jsonreply['uid']
        if(path=="/rentSpot"):
            spotnumber=jsonreply['spotNumber']
            data={
                "uid" : uid,
                "spot":spotnumber,
                "dateStart":str(datetime.today().time()).split(".")[0],
                "status":"pending"
            }
            docs = db.collection(u'rentings') .where(u'uid', u'==', uid).stream()
            for doc in docs:
                #if there is a renting , quit
                self.send_error(403,message="There is already a Rent under your account , you can not rent two spots at once")
                return
            db.collection("rentings").add(data)
            updateStatus(spotnumber,0)
        elif path=="/cancelRent":
            spotnumber = jsonreply['spotNumber']
            docs = db.collection(u'rentings').where(u'uid', u'==', uid).stream()
            count=0
            for doc in docs:
                count+=1
                db.collection("rentings").document(doc.id).delete()
                updateStatus(spotnumber,1)
                break
            if(count==0):

                self.send_error(404,message="you have no rent with that account")
                return
        elif self.path=="/checkStatus":
            rents=db.collection('rentings').where(u'uid',u'==',uid).stream()
            count=0
            for rent in rents:
                count+=1
                renting=db.collection(u'rentings').document(rent.id).get().to_dict()
                jsonresponse={
                    'status' : renting['status'],
                    'spot'  :   renting['spot']  }
                break
            if(count==0):
                self.send_error(404,message="You have no rentings")
                return
        elif self.path=="/openGarage":
            if Ultrasonic(echo,tri,17)>=36:
                self.send_error(403,message="Your car is far away from the garage , get Close to the door then attempt again")
                print(Ultrasonic(echo,tri,17))
                return
            print(Ultrasonic(echo,tri,17))
            spotnumber = jsonreply['spotNumber']
            docs = db.collection(u'rentings').where(u'uid', u'==', uid).where(u'status',u'==',"pending").stream()
            count = 0
            for doc in docs:
                count += 1
                db.collection("rentings").document(doc.id).update({u'status':"current"})
                processThread = threading.Thread(target=Park,args=(spotnumber,"open"))
                processThread.start()
                break
            if (count == 0):
                self.send_error(404, message="you have no rent with that account")
                return
        elif self.path=="/payRent":
            spotnumber = jsonreply['spotNumber']
            dateend = str(datetime.today().time()).split(".")[0].split(":")
            endtime = timedelta(hours=int(dateend[0]), minutes=int(dateend[1]),seconds=int(dateend[2]))
            docs = db.collection(u'rentings').where(u'uid', u'==', uid).where(u'status', u'==', "current").stream()
            count = 0
            for doc in docs:
                count += 1
                updateStatus(spotnumber,1)
                processThread = threading.Thread(target=Park, args=(spotnumber, "close"))
                processThread.start()
                starttime=doc.to_dict()['dateStart'].split(":")
                time=timedelta(hours=int(starttime[0]), minutes=int(starttime[1]),seconds=int(starttime[2]))
                hoursTaken=float(((endtime-time).seconds)/3600)
                payment=hoursTaken*priceperhour
                jsonresponse={
                    "payment" : payment
                }
                db.collection("rentings").document(doc.id).delete()
                break
            if (count == 0):
                self.send_error(404, message="you have no rent with that account")
                return

        self.send_response(200)
        self.send_header("Content-type", "text/json")
        self.end_headers()
        self.wfile.write(json.dumps(jsonresponse).encode(encoding='utf-8'))




if __name__ == "__main__":
    processThread = threading.Thread(target=FCM)
    processThread.start()
    processThread = threading.Thread(target=Fire)
    processThread.start()
    hostName = ""
    serverPort = 1245
    webServer = HTTPServer((hostName, serverPort), MyServer)
    print("Server started http://%s:%s" % (hostName, serverPort))
    try:
        webServer.serve_forever()
    except KeyboardInterrupt:
        webServer.server_close()
        print("Server stopped.")
        exit(-1)




