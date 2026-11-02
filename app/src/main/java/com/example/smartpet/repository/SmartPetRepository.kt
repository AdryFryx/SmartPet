
package com.example.smartpet.repository

import com.example.smartpet.model.FeedingLog
import com.example.smartpet.model.SmartPetData
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class SmartPetRepository {

    private val databaseRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("smartpet_simulation")

    private val historyRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("feeding_history")

    private val historyQuery: Query =
        historyRef.limitToLast(10)

    fun observePetData(
        onDataChanged: (SmartPetData) -> Unit
    ): ValueEventListener {

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(SmartPetData::class.java)
                    ?: SmartPetData()

                onDataChanged(data)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        databaseRef.addValueEventListener(listener)
        return listener
    }

    fun observeFeedingHistory(
        onDataChanged: (List<FeedingLog>) -> Unit
    ): ValueEventListener {

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val logs = snapshot.children
                    .reversed()
                    .mapNotNull {
                        it.getValue(FeedingLog::class.java)
                    }

                onDataChanged(logs)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        historyQuery.addValueEventListener(listener)
        return listener
    }

    fun getPetData(): Task<DataSnapshot> {
        return databaseRef.get()
    }

    fun getPetAge(): Task<DataSnapshot> {
        return databaseRef.child("petAge").get()
    }

    fun updatePetData(updates: Map<String, Any>): Task<Void> {
        return databaseRef.updateChildren(updates)
    }

    fun addFeedingLog(type: String, amount: String): Task<Void>? {
        val key = historyRef.push().key ?: return null

        val log = FeedingLog(
            id = key,
            type = type,
            amount = amount,
            timestamp = System.currentTimeMillis()
        )

        return historyRef.child(key).setValue(log)
    }

    fun removePetDataListener(listener: ValueEventListener) {
        databaseRef.removeEventListener(listener)
    }

    fun removeHistoryListener(listener: ValueEventListener) {
        historyQuery.removeEventListener(listener)
    }
}
