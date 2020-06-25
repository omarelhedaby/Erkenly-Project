package com.example.erkenly

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_landing2.*

class Landing : AppCompatActivity() {
    lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing2)
        auth= FirebaseAuth.getInstance()
        progress.visibility= View.INVISIBLE
        register.setOnClickListener {
            val intent= Intent(this,Registeration::class.java)
            startActivity(intent)
        }
    }
    fun Login(view: View)
    {
        var email=emailtxt.text.toString()
        var password=passwordtxt.text.toString()
        progress.visibility= View.VISIBLE

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
    }

    override fun onStart() {
        super.onStart()
        var user=auth.currentUser
        if(user!=null)
        {
            val intent= Intent(this,MainActivity::class.java)
            startActivity(intent)
            finish()
        }

    }
}
