package com.example.erkenly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.content_main.slot1
import kotlinx.android.synthetic.main.content_main.slot2
import kotlinx.android.synthetic.main.content_main.status1_Text
import kotlinx.android.synthetic.main.content_main.status2_Text
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

lateinit var notificationManager: NotificationManager
lateinit var notificationChannel: NotificationChannel
lateinit var builder: Notification.Builder
val channelid = "com.example.erkenly"
val description = "Erkenly"
var rentedSpotnum:Int=-1
var currentRent:Boolean=false
var messageRecieved:Boolean=false
class MainActivity : AppCompatActivity() {

    lateinit var auth:FirebaseAuth
    lateinit var db:FirebaseFirestore
    var status1:Int=-2
    var status2:Int=-2
    var TAG="Main"
    var siteurl="http://zeplosmarthome.ddns.net:1245"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        db= FirebaseFirestore.getInstance()
        checkStatus()
        checkRentStatus()
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        slot1.setOnClickListener {
            onSlotClick(1,status1)
        }
        slot2.setOnClickListener {
            onSlotClick(2,status2)
        }
        cancel.setOnClickListener {
            val builder:AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Cancelling Spot ${rentedSpotnum}")
            builder.setMessage("Are you sure you want to cancel renting this Spot")
            builder.setPositiveButton("Yes",{dialog: DialogInterface?, which: Int -> cancelRent(rentedSpotnum) })
            builder.setNegativeButton("No",{dialog: DialogInterface?, which: Int ->  })
            builder.show()
        }
        ParkorPay.setOnClickListener {
            val builder:AlertDialog.Builder = AlertDialog.Builder(this)
            if(currentRent)
            {
                builder.setTitle("Ending Rent of Spot ${rentedSpotnum}")
                builder.setMessage("Are you sure you want to end renting this Spot")
                builder.setPositiveButton("Yes",{dialog: DialogInterface?, which: Int -> endRent(rentedSpotnum) })
                builder.setNegativeButton("No",{dialog: DialogInterface?, which: Int ->  })
                builder.show()
            }
            else
            {
                builder.setTitle("Requesting Opening the Gate of Spot ${rentedSpotnum}")
                builder.setMessage("Are you sure you want to Open the gate? Make sure you are infront of the gate")
                builder.setPositiveButton("Yes",{dialog: DialogInterface?, which: Int -> openGarage(rentedSpotnum)  })
                builder.setNegativeButton("No",{dialog: DialogInterface?, which: Int ->  })
                builder.show()
            }
        }

    }

    override fun onRestart() {
        super.onRestart()
        checkRentStatus()
    }

    override fun onResume() {
        super.onResume()
        checkRentStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.logout-> {
                auth.signOut()
                val intent= Intent(applicationContext,Landing::class.java)
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        createNotificationChannel()
        auth= FirebaseAuth.getInstance()
        var token= FirebaseInstanceId.getInstance().token
        var db:FirebaseFirestore= FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("uid", auth.currentUser!!.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    db.collection("users").document(document.id).update(
                        mapOf(
                            "token" to token
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                Log.w("Updating Token", "Error getting documents: ", exception)
            }
    }
    fun onSlotClick(slotnum:Int,slotstatus:Int)
    {
        when (slotstatus)
        {
            1->{
                val builder:AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle("Renting Spot ${slotnum}")
                builder.setMessage("Are you sure you want to rent this Spot")
                builder.setPositiveButton("Yes",{dialog: DialogInterface?, which: Int -> rentSpot(slotnum) })
                builder.setNegativeButton("No",{dialog: DialogInterface?, which: Int ->  })
                builder.setCancelable(true)
                builder.show()
            }
            0-> {Toast.makeText(this,"Spot ${slotnum} is unavailable to rent now , wait till it is free ",Toast.LENGTH_SHORT).show()}
            -1-> {Toast.makeText(this,"Spot ${slotnum} is Out of Service ",Toast.LENGTH_SHORT).show()}
        }
    }
    fun checkRentStatus()
    {
        val queue = Volley.newRequestQueue(applicationContext)
        var url="${siteurl}/checkStatus"
        auth= FirebaseAuth.getInstance()
        val builder = Uri.parse(url).buildUpon()
        val params = mapOf<String, Any>(
            "uid" to auth.currentUser!!.uid
        )
        val jsonObject = JSONObject(params)

        val request = object : JsonObjectRequest(
            Request.Method.POST, builder.toString(), jsonObject,
            Response.Listener { response ->
                var strResp = response.toString()
                val jsonObj: JSONObject = JSONObject(strResp)
                var stat=jsonObj.getString("status")
                rentedSpotnum=jsonObj.getInt("spot")
                if (stat == "pending") {
                    rentedPending()
                } else if (stat == "current") {
                    rentedCurrent()
                }
                //val subUsers: JSONArray =jsonObj.getJSONArray("subscribed_users")
            },
            Response.ErrorListener { error ->
                noRent() //at error 404
            }) {

        }


        queue.add(request)
    }

    fun cancelRent(spotNum: Int)
    {
        val queue = Volley.newRequestQueue(applicationContext)
        var url:String="${siteurl}/cancelRent"
        val builder = Uri.parse(url).buildUpon()
        val params = mapOf<String,Any>(
            "uid" to auth.currentUser!!.uid,
            "spotNumber" to spotNum
        )
        val jsonObject = JSONObject(params)
        val request = object : JsonObjectRequest(
            Request.Method.POST, builder.toString(), jsonObject,
            Response.Listener { response ->
                var strResp = response.toString()
                val jsonObj: JSONObject = JSONObject(strResp)
                Toast.makeText(applicationContext,"Rent Cancelled Successfully",Toast.LENGTH_SHORT).show()
                rentedSpotnum=-1
                noRent()
            },
            Response.ErrorListener { error ->
                //HandleError(error)
                noRent()
            }) {
        }

        queue.add(request)
    }

    fun rentSpot (spotNum:Int)
    {

        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(applicationContext)
        var url:String="${siteurl}/rentSpot"
        val builder = Uri.parse(url).buildUpon()
        val params = mapOf<String,Any>(
            "uid" to auth.currentUser!!.uid,
            "spotNumber" to spotNum
        )
        val jsonObject = JSONObject(params)

        val request = object : JsonObjectRequest(
            Request.Method.POST, builder.toString(), jsonObject,
            Response.Listener { response ->
                var strResp = response.toString()
                val jsonObj: JSONObject = JSONObject(strResp)
                Toast.makeText(applicationContext,"Spot Rented Successfully",Toast.LENGTH_SHORT).show()
                rentedSpotnum=spotNum
                rentedPending()
            },
            Response.ErrorListener { error ->
                if(spotNum== rentedSpotnum)
                {
                    rentedCurrent()
                }
                else
                {
                    HandleError(error)
                }


            }) {
        }
        queue.add(request)
    }

    fun openGarage(spotNum: Int)
    {
        if(rentedSpotnum==-1)
        {
            Toast.makeText(this,"You have no renting pending",Toast.LENGTH_SHORT)
            noRent()
            return
        }
        else
        {
            val queue = Volley.newRequestQueue(applicationContext)
            var url:String="${siteurl}/openGarage"
            val builder = Uri.parse(url).buildUpon()
            val params = mapOf<String,Any>(
                "uid" to auth.currentUser!!.uid,
                "spotNumber" to spotNum
            )
            val jsonObject = JSONObject(params)
            val request = object : JsonObjectRequest(
                Request.Method.POST, builder.toString(), jsonObject,
                Response.Listener { response ->
                    var strResp = response.toString()
                    val jsonObj: JSONObject = JSONObject(strResp)
                    Toast.makeText(applicationContext,"Garage opened , Enter Now",Toast.LENGTH_SHORT).show()
                    rentedCurrent()
                },
                Response.ErrorListener { error ->
                     HandleError(error)
                }) {
            }
            queue.add(request)
        }
    }
    fun endRent(spotNum: Int)
    {

        val queue = Volley.newRequestQueue(applicationContext)
        var url:String="${siteurl}/payRent"
        val builder = Uri.parse(url).buildUpon()
        val params = mapOf<String,Any>(
            "uid" to auth.currentUser!!.uid,
            "spotNumber" to spotNum
        )
        val jsonObject = JSONObject(params)
        val request = object : JsonObjectRequest(
            Request.Method.POST, builder.toString(), jsonObject,
            Response.Listener { response ->
                var strResp = response.toString()
                val jsonObj: JSONObject = JSONObject(strResp)
                var spot= rentedSpotnum
                noRent()
                var payment=jsonObj.getDouble("payment").toBigDecimal().setScale(2,RoundingMode.HALF_UP)
                val builder:AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle("Payment of Rent")
                if(payment.toInt()==0)
                {
                    builder.setMessage("The Payment is free")
                }
                else {
                    builder.setMessage("The payment of the rent of spot ${spot} is ${payment}")
                }
                builder.setPositiveButton("Ok",{dialog: DialogInterface?, which: Int -> })
                builder.show()
            },
            Response.ErrorListener { error ->
                //HandleError(error)
            }) {
        }
        queue.add(request)
    }
    fun HandleError(error:VolleyError)
    {
        if(error.networkResponse==null)
        {
            Toast.makeText(this,"Error Occured",Toast.LENGTH_SHORT).show()
        }
        else
        {
           var errormessage=error.networkResponse.data.toString(Charsets.UTF_8).substringAfter("Message:").substringBefore("</p>")
            Toast.makeText(this,"Error message: ${errormessage}",Toast.LENGTH_SHORT).show()
        }
    }
    fun noRent()
    {
        parkorwait.setText("No current rentings are available")
        parkLayout.visibility= View.INVISIBLE
        currentRent=false
        rentedSpotnum=-1
    }
    fun rentedCurrent()
    {
        parkLayout.visibility=View.VISIBLE
        parkorwait.setText("Spot ${rentedSpotnum} is Rented  , Click Button to end the Rent")
        cancel.visibility=View.INVISIBLE
        ParkorPay.setImageDrawable(resources.getDrawable(R.drawable.payment))
        currentRent=true
    }
    fun rentedPending()
    {
        parkLayout.visibility=View.VISIBLE
        parkorwait.setText("Rent of Spot ${rentedSpotnum} is pending, Click Button to open the Garage")
        ParkorPay.setImageDrawable(resources.getDrawable(R.drawable.parking))
        cancel.visibility=View.VISIBLE
        currentRent=false
    }
    fun checkStatus()
    {
        val docRef = db.collection("status")
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                Log.d(TAG, "Current data: ${snapshot.documents}")
                for (index in snapshot.documents) {
                    if(index.get("slot").toString().toInt()==1)
                    {
                        status1=index.get("status").toString().toInt()
                        when(status1)
                        {
                            -1 -> { slot1.setImageDrawable(resources.getDrawable(R.drawable.fire))
                                   status1_Text.setText("Out of Service")
                                    status1_Text.setTextColor(resources.getColor(R.color.red))}
                            0 -> { slot1.setImageDrawable(resources.getDrawable(R.drawable.noparking))
                                status1_Text.setText("Not Available")
                                status1_Text.setTextColor(resources.getColor(R.color.red))}
                            1 -> { slot1.setImageDrawable(resources.getDrawable(R.drawable.carparking))
                                status1_Text.setText("Available")
                                status1_Text.setTextColor(resources.getColor(R.color.green))}
                        }
                    }
                    else
                    {
                        status2=index.get("status").toString().toInt()
                        when(status2)
                        {
                            -1 -> { slot2.setImageDrawable(resources.getDrawable(R.drawable.fire))
                                status2_Text.setText("Out of Service")
                                status2_Text.setTextColor(resources.getColor(R.color.red))}
                            0 -> { slot2.setImageDrawable(resources.getDrawable(R.drawable.noparking))
                                status2_Text.setText("Not Available")
                                status2_Text.setTextColor(resources.getColor(R.color.red))}
                            1 -> { slot2.setImageDrawable(resources.getDrawable(R.drawable.carparking))
                                status2_Text.setText("Available")
                                status2_Text.setTextColor(resources.getColor(R.color.green))}
                        }

                    }
                    if(rentedSpotnum==-1)
                    {
                        noRent()
                    }
                }
            } else {
                Log.d(TAG, "Current data: null")
            }
        }
    }

    private fun createNotificationChannel() {
        notificationManager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val descriptionText = description
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            notificationChannel =
                NotificationChannel(channelid, descriptionText, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = descriptionText
                }

            notificationChannel.enableLights(true)
            notificationChannel.enableVibration(true)
            notificationChannel.lightColor = resources.getColor(R.color.red)
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
    class MyFirebaseMessagingService : FirebaseMessagingService() {

        override fun onMessageReceived(p0: RemoteMessage?) {
            var title:String? = p0?.data?.get("title")
            var body:String? = p0?.data?.get("body")
            // Write a message to the database
            messageRecieved=true
           rentedSpotnum=-1 //messages are sent when rent is cancelled , or when there is a fire so no rentedspotnum is available

            if (p0?.data!!.isNotEmpty()) {
                val intent=Intent(applicationContext,MainActivity::class.java)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val pendingIntent= PendingIntent.getActivity(applicationContext,0,intent, PendingIntent.FLAG_UPDATE_CURRENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = Notification.Builder(applicationContext, channelid)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.drawable.carparking)
                        .setColor(resources.getColor(R.color.blue))
                        .setContentIntent(pendingIntent)
                        .setSound(alarmSound)
                        .setOnlyAlertOnce(true)

                    notificationManager.notify(0, builder.build())
                }

            }
            if (p0.notification != null) {

            }
        }
    }

}


