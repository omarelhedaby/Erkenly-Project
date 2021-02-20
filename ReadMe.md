Erkenly is an IOT Project. Where it manages car parking using an Android App. and all sensors and actuators and the HTTP server that handles the requests are made on the Raspberry pi using python
* Firebase Firestore is used for the database to save records of rentings and users.
* Firebase Cloud Messaging is used for push notifications.

Sensors and Actuators
* Servo motors used as gates of parking spots which are controlled by raspberry pi
* Ultrasonic sensor to make sure the car that is requesting the opening gate is at the gate and close enough. if it is in range the request will be processes and the gate will be opened.
* Flame sensor for security measures, if there is a fire a notification is sent to all parked users and gates are all opened

Operations Available in the App:
* Rent Parking Spot if available
* Cancel Parking Spot
* Request Opening of Gate
* Ending the Renting of the Parking Spot


