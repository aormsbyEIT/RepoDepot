package com.agjk.repodepot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.agjk.repodepot.model.DepotRepository
import com.agjk.repodepot.model.data.GitRepo
import com.agjk.repodepot.model.data.GitRepoCommits

class RepoViewModel : ViewModel() {

    fun getStoredReposForUser(username: String): LiveData<List<GitRepo.GitRepoItem>> =
        DepotRepository.getReposForUser(username)

    fun getStoredPrivateReposForUser(
        username: String,
        token: String
    ): LiveData<List<GitRepo.GitRepoItem>> =
        DepotRepository.getReposForUserPrivate(username, token)

    fun getStoredCommitsForUser(
        username: String,
        repoName: String
    ): LiveData<List<GitRepoCommits.GitRepoCommitsItem>> =
        DepotRepository.getCommitsForUser(username, repoName)

}