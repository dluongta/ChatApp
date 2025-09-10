package com.example.chatapp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object MessageListenerManager {

    private val messageListeners = mutableMapOf<String, ChildEventListener>()
    private var isListening = false

    fun startListening(context: Context) {
        if (isListening) return
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatsRef = FirebaseDatabase.getInstance().getReference("chats")

        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (chatSnapshot in snapshot.children) {
                    val chatId = chatSnapshot.key ?: continue
                    if (!chatId.contains(currentUid)) continue

                    val messagesRef = chatsRef.child(chatId).child("messages")

                    val listener = messagesRef.limitToLast(1).addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                            val senderId = snapshot.child("senderId").getValue(String::class.java)
                            val message = snapshot.child("message").getValue(String::class.java)
                            if (senderId != null && senderId != currentUid) {
                                val usersRef = FirebaseDatabase.getInstance().getReference("user").child(senderId)
                                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val senderName = userSnap.child("name").getValue(String::class.java) ?: senderId
                                        NotificationUtils.sendNotification(
                                            context,
                                            senderName,
                                            message ?: "You have a new message",
                                            senderId
                                        )
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                            }
                        }
                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onCancelled(error: DatabaseError) {}
                    })
                    messageListeners[chatId] = listener
                }
                isListening = true
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun stopListening() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatsRef = FirebaseDatabase.getInstance().getReference("chats")
        for ((chatId, listener) in messageListeners) {
            chatsRef.child(chatId).child("messages").removeEventListener(listener)
        }
        messageListeners.clear()
        isListening = false
    }
}
