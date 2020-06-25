from firebase_admin import credentials, firestore, messaging
import firebase_admin
from datetime import datetime,timedelta

cred = credentials.Certificate("./ErkenlyService.json")
app = firebase_admin.initialize_app(cred)
db = firestore.client()
times=[]
tokens=[]
id=[]
interval=0.5

def updateStatus(spotnumber,stat):
    spots = db.collection(u'status').where(u'slot', u'==', spotnumber).stream()
    for spot in spots:
        db.collection(u'status').document(spot.id).update({'status': stat})

def FCmessage(token):
    message = messaging.Message(
        data={
            'title': 'Rent Cancelled',
            'body': 'You have exceeded the Interval allowed to reserve a place',
        },
        token=token,
    )
    response = messaging.send(message)
    # Response is a message ID string.
    print('Successfully sent message:', response)


# Create a callback on_snapshot function to capture changes
def on_snapshot(doc_snapshot,changes, read_time):
    times.clear()
    tokens.clear()
    id.clear()
    for rents in doc_snapshot:
        if(rents.to_dict()['status']=="pending"):
            id.append(rents.id)
            times.append(rents.to_dict()['dateStart'])
            uid = rents.to_dict()['uid']
            users = db.collection(u'users').where('uid', '==', uid).stream()
            for user in users:
                tokens.append(user.to_dict()['token'])
                break


doc_ref = db.collection(u'rentings')
doc_watch = doc_ref.on_snapshot(on_snapshot)
while 1:
    timenow = datetime.today().time()
    now = timedelta(hours=timenow.hour, minutes=timenow.minute,seconds=timenow.second)
    for idx,i in enumerate(times):
        time=i.split(":")
        then=timedelta(hours=int(time[0]),minutes=int(time[1]),seconds=int(time[2]))
        sub=now-then
        if float(sub.seconds/60)>= interval:
            try:
                FCmessage(tokens[idx])
            except:
                continue
            try:
                spot=db.collection('rentings').document(id[idx]).get().to_dict()['spot']
            except:
                continue
            updateStatus(spot,1)
            db.collection('rentings').document(id[idx]).delete()






