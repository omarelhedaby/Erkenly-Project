package com.example.erkenly

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_registeration.*

class Registeration : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    lateinit var db:FirebaseFirestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registeration)
        auth= FirebaseAuth.getInstance()
        progress.visibility= View.INVISIBLE
        db= FirebaseFirestore.getInstance()

    }
    fun Registerbtn(view: View)
    {
        var email=emailtxt.text.toString()
        var password=passwordtxt.text.toString()
        progress.visibility= View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progress.visibility= View.INVISIBLE
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Toast.makeText(baseContext, "Registeration success",
                        Toast.LENGTH_SHORT).show()
                    var uid=auth.currentUser!!.uid
                    var user= mapOf(
                        "uid" to uid,
                        "email" to email,
                        "token" to 0
                    )
                    db.collection("users").add(user)
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            progress.visibility= View.INVISIBLE
                            if (task.isSuccessful) {
                                // Sign in success, update UI with the signed-in user's information
                                val user = auth.currentUser
                                val intent= Intent(this,MainActivity::class.java)
                                startActivity(intent)
                                finish()

                            } else {
                                // If sign in fails, display a message to the user.
                                Toast.makeText(baseContext, "User not Found",
                                    Toast.LENGTH_SHORT).show()

                            }

                            // ...
                        }
                } else {
                    Log.w("ERROR", "createUserWithEmail:failure", task.exception)
                    // If sign in fails, display a message to the user.
                    Toast.makeText(baseContext, "Registeration failed.",
                        Toast.LENGTH_SHORT).show()
                }

                // ...
            }

    }

}
