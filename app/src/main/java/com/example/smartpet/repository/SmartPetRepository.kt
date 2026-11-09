package com.example.smartpet.repository

import com.example.smartpet.model.FeedingLog
import com.example.smartpet.model.SmartPetData
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class SmartPetRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Documento principal de telemetría dentro de la colección "smartpet"
    private val petDocRef: DocumentReference =
        firestore.collection("smartpet").document("telemetry")

    // Subcolección para el historial de raciones
    private val historyCollectionRef: CollectionReference =
        petDocRef.collection("feeding_history")

    /**
     * Escucha en tiempo real el documento de la mascota en Firestore.
     */
    fun observePetData(
        onDataChanged: (SmartPetData) -> Unit
    ): ListenerRegistration {

        return petDocRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.toObject(SmartPetData::class.java) ?: SmartPetData()
                onDataChanged(data)
            } else if (snapshot != null && !snapshot.exists()) {
                // Si el documento aún no existe en Firestore, lo crea por primera vez
                petDocRef.set(SmartPetData())
            }
        }
    }

    /**
     * Escucha en tiempo real las últimas 10 raciones registradas.
     */
    fun observeFeedingHistory(
        onDataChanged: (List<FeedingLog>) -> Unit
    ): ListenerRegistration {

        return historyCollectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FeedingLog::class.java)
                    }
                    onDataChanged(logs)
                }
            }
    }

    /**
     * Obtiene el documento actual una sola vez.
     */
    fun getPetData(): Task<DocumentSnapshot> {
        return petDocRef.get()
    }

    /**
     * Obtiene la edad de la mascota una sola vez.
     */
    fun getPetAge(): Task<DocumentSnapshot> {
        return petDocRef.get()
    }

    /**
     * Actualiza campos específicos en Firestore.
     */
    fun updatePetData(updates: Map<String, Any>): Task<Void> {
        return petDocRef.update(updates)
    }

    /**
     * Agrega un nuevo registro de ración a la subcolección en Firestore.
     */
    fun addFeedingLog(type: String, amount: String): Task<Void>? {
        val newDocRef = historyCollectionRef.document()
        val log = FeedingLog(
            id = newDocRef.id,
            type = type,
            amount = amount,
            timestamp = System.currentTimeMillis()
        )
        return newDocRef.set(log)
    }

    /**
     * Detiene la escucha en tiempo real de Firestore.
     */
    fun removePetDataListener(listener: ListenerRegistration) {
        listener.remove()
    }

    fun removeHistoryListener(listener: ListenerRegistration) {
        listener.remove()
    }
}