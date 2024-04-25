/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.viewmodel

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Account
import org.linphone.core.ConfiguringState
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.Event

class DrawerMenuViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Drawer Menu ViewModel]"
    }

    val accounts = MutableLiveData<ArrayList<AccountModel>>()

    val hideAddAccount = MutableLiveData<Boolean>()

    val hideRecordings = MutableLiveData<Boolean>()

    val hideSettings = MutableLiveData<Boolean>()

    val startAssistantEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val closeDrawerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showAccountPopupMenuEvent: MutableLiveData<Event<Pair<View, Account>>> by lazy {
        MutableLiveData<Event<Pair<View, Account>>>()
    }

    val defaultAccountChangedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onDefaultAccountChanged(core: Core, account: Account?) {
            if (account == null) {
                Log.w("$TAG Default account is now null!")
            } else {
                Log.i(
                    "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has been set as default"
                )
                for (model in accounts.value.orEmpty()) {
                    if (model.account != account) {
                        model.isDefault.postValue(false)
                    }
                }
                defaultAccountChangedEvent.postValue(
                    Event(account.params.identityAddress?.asStringUriOnly() ?: "")
                )
            }
        }

        @WorkerThread
        override fun onAccountAdded(core: Core, account: Account) {
            Log.i(
                "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has been added to the Core"
            )
            computeAccountsList()
        }

        @WorkerThread
        override fun onAccountRemoved(core: Core, account: Account) {
            Log.i(
                "$TAG Account [${account.params.identityAddress?.asStringUriOnly()}] has been removed from the Core"
            )
            computeAccountsList()
        }

        @WorkerThread
        override fun onConfiguringStatus(core: Core, status: ConfiguringState?, message: String?) {
            if (status != ConfiguringState.Skipped) {
                accounts.value.orEmpty().forEach(AccountModel::destroy)

                Log.i("$TAG Configuring status is [$status], reload accounts")
                computeAccountsList()
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            hideRecordings.postValue(corePreferences.disableCallRecordings)
            hideSettings.postValue(corePreferences.hideSettings)

            computeAccountsList()
        }
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            accounts.value.orEmpty().forEach(AccountModel::destroy)
        }

        super.onCleared()
    }

    @UiThread
    fun closeDrawerMenu() {
        closeDrawerEvent.value = Event(true)
    }

    @UiThread
    fun addAccount() {
        startAssistantEvent.value = Event(true)
    }

    @UiThread
    fun updateAccountsList() {
        coreContext.postOnCoreThread {
            computeAccountsList()
        }
    }

    @WorkerThread
    private fun computeAccountsList() {
        accounts.value.orEmpty().forEach(AccountModel::destroy)

        val list = arrayListOf<AccountModel>()
        for (account in coreContext.core.accountList) {
            val model = AccountModel(account) { view, account ->
                // onClicked
                showAccountPopupMenuEvent.postValue(Event(Pair(view, account)))
            }
            list.add(model)
        }
        accounts.postValue(list)

        hideAddAccount.postValue(corePreferences.oneAccountMax && list.isNotEmpty())
    }
}
