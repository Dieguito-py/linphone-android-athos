/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.contacts

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.*

class ContactData(val friend: Friend) {
    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val name = MutableLiveData<String>()

    val avatar = getAvatarUri()

    private val friendListener = object : FriendListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(fr: Friend) {
            presenceStatus.postValue(fr.consolidatedPresence)
        }
    }

    init {
        name.postValue(friend.name)
        presenceStatus.postValue(friend.consolidatedPresence)

        friend.addListener(friendListener)

        presenceStatus.postValue(ConsolidatedPresence.Offline)
    }

    @WorkerThread
    fun onDestroy() {
        friend.removeListener(friendListener)
    }

    @WorkerThread
    private fun getAvatarUri(): Uri? {
        val refKey = friend.refKey
        if (refKey != null) {
            val lookupUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                refKey.toLong()
            )
            return Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        }

        return null
    }
}
