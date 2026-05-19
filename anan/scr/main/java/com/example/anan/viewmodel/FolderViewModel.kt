package com.example.anan.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.anan.model.KnowledgeDocument
import com.example.anan.model.KnowledgeFolder

class FolderViewModel(private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("anan_folders", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _folders = MutableStateFlow<List<KnowledgeFolder>>(loadFolders())
    val folders: StateFlow<List<KnowledgeFolder>> = _folders

    private fun loadFolders(): List<KnowledgeFolder> {
        return try {
            val json = prefs.getString("folders_list", "[]")
            if (json.isNullOrEmpty() || json == "[]") {
                emptyList()
            } else {
                val type = object : TypeToken<List<KnowledgeFolder>>() {}.type
                gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveFolders() {
        viewModelScope.launch {
            val json = gson.toJson(_folders.value)
            prefs.edit().putString("folders_list", json).apply()
        }
    }

    fun addFolder(name: String) {
        val newFolder = KnowledgeFolder(name = name)
        _folders.value = _folders.value + newFolder
        saveFolders()
    }

    fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        saveFolders()
    }

    fun addDocument(folderId: String, documentName: String, filePath: String = "") {
        val currentFolders = _folders.value.toMutableList()
        val folderIndex = currentFolders.indexOfFirst { it.id == folderId }
        if (folderIndex != -1) {
            val folder = currentFolders[folderIndex]
            val newDocument = KnowledgeDocument(
                name = documentName,
                filePath = filePath
            )
            val updatedDocuments = folder.documents + newDocument
            currentFolders[folderIndex] = folder.copy(documents = updatedDocuments)
            _folders.value = currentFolders.toList()
            saveFolders()
        }
    }

    fun deleteDocument(folderId: String, documentId: String) {
        val currentFolders = _folders.value.toMutableList()
        val folderIndex = currentFolders.indexOfFirst { it.id == folderId }
        if (folderIndex != -1) {
            val folder = currentFolders[folderIndex]
            val updatedDocuments = folder.documents.filter { it.id != documentId }
            currentFolders[folderIndex] = folder.copy(documents = updatedDocuments)
            _folders.value = currentFolders.toList()
            saveFolders()
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        val currentFolders = _folders.value.toMutableList()
        val folderIndex = currentFolders.indexOfFirst { it.id == folderId }
        if (folderIndex != -1) {
            val folder = currentFolders[folderIndex]
            currentFolders[folderIndex] = folder.copy(name = newName)
            _folders.value = currentFolders.toList()
            saveFolders()
        }
    }

    fun getFolder(folderId: String): KnowledgeFolder? {
        return _folders.value.find { it.id == folderId }
    }
}
