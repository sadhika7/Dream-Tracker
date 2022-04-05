package edu.vt.cs.cs5254.dreamcatcher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import java.io.File
import java.util.*

class DreamDetailViewModel : ViewModel() {

    private val dreamRepository = DreamRepository.get()
    private val dreamIdLiveData = MutableLiveData<UUID>()

    var dreamLiveData: LiveData<DreamWithEntries> =
        Transformations.switchMap(dreamIdLiveData){
                dreamId -> dreamRepository.getDreamWithEntries(dreamId)
        }

    fun loadDreamEntry(dreamId: UUID){
        dreamIdLiveData.value = dreamId
    }

    fun saveDreamEntry(dreamWithEntry: DreamWithEntries){
        dreamRepository.updateDreamWithEntries(dreamWithEntry)
    }

    fun getPhotoFile(dream: Dream): File {
        return dreamRepository.getPhotoFile(dream)
    }

}